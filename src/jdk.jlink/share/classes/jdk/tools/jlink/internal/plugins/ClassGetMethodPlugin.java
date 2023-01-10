package jdk.tools.jlink.internal.plugins;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;
import jdk.internal.org.objectweb.asm.tree.analysis.Frame;
import jdk.tools.jlink.internal.ClassValue;
import jdk.tools.jlink.internal.ConstantInterpreter;
import jdk.tools.jlink.internal.StringValue;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import java.util.*;

public class ClassGetMethodPlugin implements Plugin {

    public static final String NAME = "class-get-method";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Category getType() {
        return Category.TRANSFORMER;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public String getArgumentsDescription() {
        return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public boolean hasArguments() {
        return false;
    }

    @Override
    public void configure(Map<String, String> config) {
        // TODO ?
    }


    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        Objects.requireNonNull(in);
        Objects.requireNonNull(out);

        in.entries()
                .forEach(resource -> {
                    String path = resource.path();
                    // TODO remove java.base
                    if (path.endsWith(".class") && !path.endsWith("/module-info.class") && ! resource.moduleName().equals("java.base")) {
                        out.add(transform(resource, in));
                    } else {
                        out.add(resource);
                    }
                });


        return out.build();
    }

    private ResourcePoolEntry transform(ResourcePoolEntry resource, ResourcePool pool) {
        byte[] inBytes = resource.contentBytes();
        ClassReader cr = new ClassReader(inBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        List<MethodNode> ms = cn.methods;
        boolean modified = false;

        for (MethodNode mn : ms) {
            Analyzer<BasicValue> analyzer = new Analyzer<>(new ConstantInterpreter());
            try {
                analyzer.analyze(cn.name, mn);
            } catch (AnalyzerException e) {
                throw new RuntimeException();
            }

            InsnList il = mn.instructions;
            Map<AbstractInsnNode, Method> callMap = new HashMap<>();
            int instructionIndex = 0;
            Frame<BasicValue>[] frames = analyzer.getFrames();
            ListIterator<AbstractInsnNode> iterator = il.iterator();
            while (iterator.hasNext()) {
                AbstractInsnNode insn = iterator.next();
                if (insn instanceof MethodInsnNode min) {
                    if (min.getOpcode() == Opcodes.INVOKEVIRTUAL && min.name.equals("getMethod")
                            && min.owner.equals("java/lang/Class")) {

                        /* Get parameters to Class.getMethod */
                        List<BasicValue> params = getParameters(instructionIndex, frames);

                        /* Verify whether the argument for the method params is an empty array */
                        if (min.getPrevious() instanceof TypeInsnNode anewarray
                                && min.getPrevious().getPrevious() instanceof InsnNode zero) {
                            if (anewarray.getOpcode() == Opcodes.ANEWARRAY && zero.getOpcode() == Opcodes.ICONST_0) {
                                System.out.println("empty array as params");

                                Method method = new Method(params);
                                if (min.getNext() instanceof VarInsnNode var) {
                                    callMap.put(var, method);
                                } else {
                                    callMap.put(min, method);
                                }
                            }
                        }


                    } else if (min.getOpcode() == Opcodes.INVOKEVIRTUAL && min.name.equals("invoke")
                            && min.owner.equals("java/lang/reflect/Method")) {

                        List<AbstractInsnNode> toRemove = new ArrayList<>();
                        AbstractInsnNode prev = min.getPrevious();
                        Method method = null;
                        while (prev != null) {
                            if (prev instanceof VarInsnNode var && var.getOpcode() == Opcodes.ALOAD) {
                                /* Get the position of where it was loaded from in memory */
                                int pos = var.var;
                                method = getMethodInPos(pos, callMap);
                            } else if (prev instanceof MethodInsnNode methodInsn)  {
                                if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
                                        && methodInsn.name.equals("getMethod")
                                        && methodInsn.owner.equals("java/lang/Class")) {
                                    method = callMap.get(methodInsn);
                                }
                            }

                            if (method != null) break;
                            toRemove.add(prev);
                            prev = prev.getPrevious();
                        }

                        if (method != null) {
                            /* Insert bytecode equivalent of invocation */
                            MethodInsnNode invoke = new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    method.className,
                                    method.methodName,
                                    method.desc);
                            il.set(min, invoke);

                            /* Remove unnecessary instructions */
                            for (AbstractInsnNode r : toRemove) {
                                il.remove(r);
                            }

                            modified = true;
                        }
                    }
                } else if (insn instanceof VarInsnNode var && var.getOpcode() == Opcodes.ALOAD) {
                    int pos = var.var;
                    Method method = getMethodInPos(var.var, callMap);
                    if (method != null) {

                        int aloadCount = 1;
                        AbstractInsnNode next = var.getNext();
                        while (next != null) {
                            // TODO check what happens with a pop? Need test case to manually create bytecode.

                            if (next instanceof VarInsnNode store) {
                                if (store.getOpcode() == Opcodes.ALOAD) {
                                    aloadCount++;
                                } else if (store.getOpcode() == Opcodes.ASTORE) {
                                    aloadCount--;
                                    if (aloadCount == 0) {
                                        // replace in map
                                        replacePositionInMap(pos, store, callMap);
                                        break;
                                    }
                                }
                            } else if (next instanceof MethodInsnNode methodInsn) {
                                if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
                                        && methodInsn.name.equals("invoke")
                                        && methodInsn.owner.equals("java/lang/reflect/Method")) {
                                    aloadCount--;
                                    if (aloadCount == 0) break;
                                }
                            }
                            next = next.getNext();
                        }

                        if (next == null) {
                            /* Means we loaded and didn't store back */
                            replacePositionInMap(pos, null, callMap);
                        }
                    }
                }
                instructionIndex++;
            }

        }

        if (modified) {
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);

            return resource.copyWithContent(cw.toByteArray());
        }


       return resource;
    }

    /**
     * Replaces the VarInsnNode associated with a method if it changed position in memory or removes
     * method call from map if value was loaded but not used anywhere.
     * @param oldPos
     * @param newNode
     * @param callMap
     */
    private void replacePositionInMap(int oldPos, AbstractInsnNode newNode, Map<AbstractInsnNode, Method> callMap) {
        AbstractInsnNode toRemove = null;
        Method method = null;
        for (AbstractInsnNode node : callMap.keySet()) {
            if (node instanceof VarInsnNode var && var.var == oldPos) {
                toRemove = node;
                method = callMap.get(node);
            }
        }

        callMap.remove(toRemove);
        if (newNode != null) {
            callMap.put(newNode, method);
        }
    }

    private Method getMethodInPos(int pos, Map<AbstractInsnNode, Method> callMap) {
        for (AbstractInsnNode node : callMap.keySet()) {
            if (node instanceof VarInsnNode var && var.var == pos) {
                return callMap.get(node);
            }
        }
        return null;
    }

    private void modifyInstructions(InsnList il, List<BasicValue> params, String returnType) {

        /* Create method handle instructions */



    }

    private List<BasicValue> getParameters(int instructionIndex,  Frame<BasicValue>[] frames) {
        List<BasicValue> params = new ArrayList<>();
        boolean traversed = false;
        int index = 0;
        while (! traversed) {
            BasicValue arg;
            try {
                arg = getStackValue(instructionIndex, index, frames);

                if (arg != null) {
                    params.add(arg);
                } else {
                    traversed = true;
                }
            } catch (AnalyzerException e) {
                throw new RuntimeException(e); // TODO not sure about this handling
            }
            index++;
        }

        Collections.reverse(params);
        return params;
    }

    private BasicValue getStackValue(int instructionIndex, int frameIndex, Frame<BasicValue>[] frames) throws AnalyzerException {
        Frame<BasicValue> f = frames[instructionIndex];
        if (f == null) {
            return null;
        }
        int top = f.getStackSize() - 1;
        return frameIndex <= top ? f.getStack(top - frameIndex) : null;
    }

    class Method {
        String methodName;
        String className;
        String desc;


        public Method(String methodName, String className) {
            this.methodName = methodName;
            this.className = className;
            desc = "()V";
        }

        public Method(List<BasicValue> params) {
            if (params.get(0) instanceof ClassValue cv) {
                this.className = cv.getName();
            }
            if (params.get(1) instanceof StringValue sv) {
                this.methodName = sv.getContents();
            }
            desc = "()V";
        }

        public String getMethodName() {
            return methodName;
        }

        public String getClassName() {
            return className;
        }

        public String getDesc() {
            return desc;
        }
    }

}
