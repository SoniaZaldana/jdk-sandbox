package jdk.tools.jlink.internal.plugins;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.tools.jlink.internal.PrimitiveUtils;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.plugin.*;

import java.util.*;

public class InheritanceGraphPlugin implements Plugin, ResourcePrevisitor {

    public static final String name = "inheritance-graph-plugin";
    static HierarchyContext context = new HierarchyContext();
    public ClassVisitor getInheritanceInfoProcessor() {
        return new ClassNode(Opcodes.ASM9) {

            @Override
            public void visit(int version,
                              int access,
                              String name,
                              String signature,
                              String superName,
                              String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                context.addSuperClass(name, superName);
                context.addClassNode(this);
                for (String inf : interfaces) {
                    context.addSuperInterface(name, inf);
                }
            }

            @Override
            public MethodVisitor visitMethod(
                    final int access,
                    final String name,
                    final String descriptor,
                    final String signature,
                    final String[] exceptions) {
                JlinkMethodNode method = new JlinkMethodNode(Opcodes.ASM9, access, name, descriptor, signature,
                        exceptions, this.name);
                methods.add(method);
                return method;
            }
        };

    }

    @Override
    public void previsit(ResourcePool resources, StringTable strings) {
        Objects.requireNonNull(resources);
        resources.entries()
                .forEach(resource -> {
                    String path = resource.path();
                    if (path.endsWith(".class") && !path.endsWith("/module-info.class")) {
                        ClassReader cr = new ClassReader(resource.contentBytes());
                        cr.accept(getInheritanceInfoProcessor(), 0);
                    }
                });

        context.computeClosure();
    }

    // determines if class1 is either the same as, or is a superclass or superinterface of class2.
    // If class1 represents a primitive type, this method returns true if class2 is exactly the same as
    // class 1, otherwise it returns false.
    private static boolean isAssignableFrom(String class1, String class2) {
        if (class1.equals(class2)) {
            return true;
        } else if (PrimitiveUtils.isPrimitive(class1))  {
            return false;
        } else {
            List<String> superClasses = context.getSuperClasses(class2);
            List<String> superInterfaces = context.getSuperInterfaces(class2);
            return superClasses.contains(class1) || superInterfaces.contains(class1);
        }
    }

    public static MethodNode getMethod(String className, String methodName, String desc) {
        MethodList res = getMethodRecursive(className, methodName, getParams(desc), true /* includeStatic */);
        return res == null ? null : res.getMostSpecific();
    }

    public static MethodList getMethodRecursive(String className, String methodName,
                                                      List<String> params,  boolean includeStatic) {

        // first check public methods in the class
        ClassNode cn = context.getClassNode(className);
        List<MethodNode> publicMethods = getPublicMethods(cn);
        MethodList res = MethodList.filterForMatches(publicMethods, methodName, params, includeStatic);

        // if at least one match is found, we don't need to look further
        if (res != null) {
            return res;
        }

        // if there was no match among declared methods,
        // we must consult the superclass (if any) recursively
        String sc = cn.superName;
        if (sc != null) {
            res = getMethodRecursive(sc, methodName, params, includeStatic);
        }

        // ... and coalesce the superclass methods with the methods obtained from directly
        // implemented interfaces excluding static methods
        for (String inf : cn.interfaces) {
            res = MethodList.merge(res, getMethodRecursive(inf, methodName, params, /*includeStatic */ false));
        }

        return res;
    }

    private static boolean isMatch(MethodNode mn, String methodName, List<String> params) {
        List<String> mParams = getParams(mn.desc);
        boolean paramMatch = true;
        if (mParams.size() == params.size()) {
            for (int i = 0; i < params.size(); i++) {
                String passedParam = params.get(i);
                String methodParam = mParams.get(i);
                if (! passedParam.equals(methodParam) && ! isAssignableFrom(methodParam, passedParam)) {
                    paramMatch = false;
                }
            }
            return mn.name.equals(methodName) && paramMatch;
        }

        return false;
    }

    private static List<MethodNode> getPublicMethods(ClassNode cn) {
        return cn.methods.stream().filter(mn -> (mn.access & Opcodes.ACC_PUBLIC) != 0).toList();
    }

    private static boolean isStatic(int access) {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    private static List<String> getParams(String desc) {
        String[] params = desc.substring(1, desc.indexOf(")")).split(";");
        for (int i = 0; i < params.length; i++) {
            if (params[i].length() > 1) {
                params[i] = params[i].substring(1);
            }
        }

        return Arrays.stream(params).toList();
    }

    private static String getReturnType(MethodNode mn) {
        String returnType = mn.desc.substring(mn.desc.indexOf(")") + 1);
        returnType = returnType.endsWith(";") ? returnType.substring(0, returnType.length() - 1) : returnType;
        if (returnType.length() > 1) return returnType.substring(1);
        return returnType;
    }

    private static boolean isInterface(ClassNode cn) {
        return (cn.access & Opcodes.ACC_INTERFACE) != 0;
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        return in;
    }

    static final class MethodList {
        MethodNode mn;
        MethodList next;

        private MethodList(MethodNode mn) {
            this.mn = mn;
        }

        static MethodList filterForMatches(List<MethodNode> methods, String methodName,
                                                         List<String> params, boolean includeStatic) {
            MethodList head = null, tail = null;
            for (MethodNode mn : methods) {
                if ((includeStatic || isStatic(mn.access))
                        && isMatch(mn, methodName, params)) {
                    if (tail == null) {
                        head = tail = new MethodList(mn);
                    } else {
                        tail = tail.next = new MethodList(mn);
                    }
                }
            }
            return head;
        }

         MethodNode getMostSpecific() {
            MethodNode m = this.mn;
             String rt = getReturnType(m);

             for (MethodList ml = next; ml != null; ml = ml.next) {
                 MethodNode m2 = ml.mn;
                 String rt2 = getReturnType(m2);
                 if (! rt2.equals(rt) && isAssignableFrom(rt, rt2)) {
                     // found more specific return type
                     m = m2;
                     rt = rt2;
                 }
             }
             return m;
        }

        /**
         * This method should only be called with the {@code head} (possibly null)
         * of a list of MethodNode(s) that share the same (method name, parameter types)
         * and another {@code methodList} that also contains MethodNode(s) with the
         * same and equal (method name, parameter types) as the 1st list.
         * It modifies the 1st list and returns the head of merged list
         * containing only the most specific methods for each signature
         * (i.e. return type). The returned head of the merged list may or
         * may not be the same as the {@code head} of the given list.
         * The given {@code methodList} is not modified.
         */
        static MethodList merge (MethodList head, MethodList methodList) {
            for (MethodList ml = methodList; ml != null; ml = ml.next) {
                head = merge(head, ml.mn);
            }
            return head;
        }

        private static MethodList merge(MethodList head, MethodNode mn) {


            ClassNode dclass = context.getClassNode(((JlinkMethodNode) mn).className);
            String rtype = getReturnType(mn);
            MethodList prev = null;
            for (MethodList l = head; l != null; l = l.next) {
                // eXisting method
                MethodNode xmethod = l.mn;
                // only merge methods with same signature:
                // (return type, name, parameter types) tuple
                // as we only keep methods with same (name, parameter types)
                // tuple together in one list, we only need to check return type
                if (rtype.equals(getReturnType(xmethod))) {
                    ClassNode xdclass = context.getClassNode(((JlinkMethodNode) xmethod).className);

                    if (isInterface(dclass) == isInterface(xdclass)) {
                        // both methods are declared by interfaces
                        // or both by classes
                        if (isAssignableFrom(dclass.name, xdclass.name)) {
                            // existing method is the same or overrides
                            // new method - ignore new method
                            return head;
                        }
                        if (isAssignableFrom(dclass.name, xdclass.name)) {
                            // new method overrides existing
                            // method - knock out existing method
                            if (prev != null) {
                                prev.next = l.next;
                            } else {
                                head = l.next;
                            }
                            // keep iterating
                        } else {
                            // unrelated (should only happen for interfaces)
                            prev = l;
                            // keep iterating
                        }
                    } else if (isInterface(dclass)) {
                        // new method is declared by interface while
                        // existing method is declared by class -
                        // ignore new method
                        return head;
                    } else /* xdclass is interface */ {
                        // new method is declared by class while
                        // existing method is declared by interface -
                        // knock out existing method
                        if (prev != null) {
                            prev.next = l.next;
                        } else {
                            head = l.next;
                        }
                        // keep iterating
                    }
                } else {
                    // distinct signatures
                    prev = l;
                    // keep iterating
                }
            }
            // append new method to the list
            if (prev == null) {
                head = new MethodList(mn);
            } else {
                prev.next = new MethodList(mn);
            }
            return head;
        }
    }

    class JlinkMethodNode extends MethodNode {
        String className;
        public JlinkMethodNode(
                final int api,
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions,
                final String className) {
            super(api, access, name, descriptor, signature, exceptions);
            this.className = className;
        }
    }
}
