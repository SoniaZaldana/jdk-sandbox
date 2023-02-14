package jdk.tools.jlink.internal.plugins;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;
import jdk.tools.jlink.internal.*;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClassGetMethodPlugin implements Plugin {

    public static final String NAME = "class-get-method";
    private final DependencyPluginFactory factory;
    private final String REPORT_FILE_NAME = "cgm_report.txt";

    private final String REPORT_UNTRANSFORMED = "cgm_untransformed.txt";
    private final FileWriter reportWriter;
    private final FileWriter untransformedWriter;


    public ClassGetMethodPlugin() {
        this.factory = new DependencyPluginFactory();

        try {
            reportWriter = new FileWriter(REPORT_FILE_NAME);
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            Date date = new Date();
            reportWriter.write(String.format("------------ Class.getMethod Plugin Transformation " +
                    "Report generated On %s ------------\n", formatter.format(date)));


            untransformedWriter = new FileWriter(REPORT_UNTRANSFORMED);
            untransformedWriter.write(String.format("------------ Class.getMethod Plugin Transformation " +
                    "Report generated On %s ------------\n", formatter.format(date)));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
        // TODO once i figure out exact config options
    }

    @Override
    public List<Plugin> requiredPlugins() {
        return factory.create();
    }


    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        Objects.requireNonNull(in);
        Objects.requireNonNull(out);

        in.entries()
                .forEach(resource -> {
                    String path = resource.path();
                    // TODO remove java.base when finished testing
                    if (path.endsWith(".class") && !path.endsWith("/module-info.class")) {
                        out.add(transform(resource, in));
                    } else {
                        out.add(resource);
                    }
                });

        try {
            reportWriter.close();
            untransformedWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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
            TreeMap<int[],AbstractInsnNode> conditionals = new TreeMap<>(
                    Comparator.comparingInt((int[] a) -> a[0]).thenComparingInt(a -> a[1]));
            Map<AbstractInsnNode, List<SourceValue>> sources = new HashMap<>();
            InsnList il = mn.instructions;
            Analyzer<BasicValue> constantAnalyzer = new Analyzer<>(new ConstantInterpreter());
            Analyzer<SourceValue> sourceAnalyzer = new Analyzer<>(new ParameterSourceInterpreter(Opcodes.ASM9, sources)) {
                @Override
                protected void newControlFlowEdge(int insn, int successor) {
                    if(insn != successor - 1) {
                        AbstractInsnNode instruction = il.get(insn);
                        List<SourceValue> dep = sources.get(instruction);
                        if(dep != null && !dep.isEmpty())
                            conditionals.put(new int[]{ insn, successor }, instruction);
                    }
                }
            };
            try {
                constantAnalyzer.analyze(cn.name, mn);
                sourceAnalyzer.analyze(cn.name, mn);
            } catch (AnalyzerException e) {
                throw new RuntimeException();
            }

            Map<MethodInsnNode, List<AbstractInsnNode>> methodMap = new HashMap<>();
            int instructionIndex = 0;
            Frame<BasicValue>[] frames = constantAnalyzer.getFrames();
            ListIterator<AbstractInsnNode> iterator = il.iterator();
            while (iterator.hasNext()) {
                AbstractInsnNode insn = iterator.next();
                if (insn instanceof MethodInsnNode min) {
                    if (min.getOpcode() == Opcodes.INVOKEVIRTUAL && min.name.equals("invoke")
                            && min.owner.equals("java/lang/reflect/Method")) {

                        List<BasicValue> params = getStackValues(instructionIndex, frames);

                        TransformationEntry entry = new TransformationEntry(cn.name, min.name, il.indexOf(min), resource.moduleName());
                        for (BasicValue value : params) {
                            entry.addParameter(value.toString());
                        }

                        if (params.get(0) instanceof MethodValue mv
                                && !mv.isDeclared()
                                // FIXME: can remove above condition once I add more code to determine when is it safe to remove those calls.
                                && !isInitMethod(mv.getMethodName())
                                && params.get(2) instanceof ArrayValue av) {

                            List<AbstractInsnNode> inputs = getInputs(min, sources, il, conditionals, true);

                            MethodNode matchingMethodNode = InheritanceGraphPlugin.
                                    getMethod(mv.getClassName().replace(".", "/"),
                                            mv.getMethodName(),
                                            mv.getDesc().replace(".", "/"),
                                            mv.isDeclared());

                            /* Only do transformation if we find method, otherwise let reflective method do
                               the exception handling.
                             */
                            if (matchingMethodNode != null) {
                                if (! mv.getDesc().startsWith("()")) {
                                    /** If the method invocation takes parameters, there is work to be done
                                     * to unpack these parameters for the Method.invoke() call.
                                     * Note, that Method.invoke() takes an array containing the parameters
                                     * passed to the invocation, whereas the invocation is expecting
                                     * values on the operand stack - not an array reference.
                                     * We need to do some adjusting to pass the parameters as expected.*/

                                    List<String> invokeParamsType = mv.getSplitDesc();
                                    AbstractInsnNode load = inputs.get(1);
                                    if (load.getOpcode() == Opcodes.ANEWARRAY) {
                                        /**
                                         * This is the case where the array is created within the method invocation.
                                         *      e.g. m.invoke(t, new Object[] {3,4}) or m.invoke(t, 3, 4).
                                         * <p>
                                         * In this case, we "normalize" the call by transforming the calls above
                                         * to the following:
                                         *      Object[] args = {3, 4};
                                         *      m.invoke(t, args).
                                         * <p>
                                         * We then handle "array unpacking" in the second invocation case.
                                         */

                                        /** Fetch all instructions related to creating and clone them */
                                        InsnList arrayInsns = new InsnList();
                                        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
                                        List<AbstractInsnNode> arrayInsnList = getArrayInstructions(il.indexOf(load) -1 ,il.indexOf(min), il, labelMap);
                                        for (AbstractInsnNode i : arrayInsnList) {
                                            arrayInsns.add(i.clone(labelMap));
                                        }
                                        arrayInsns.add(new VarInsnNode(Opcodes.ASTORE, mn.maxLocals));


                                        /** Remove instructions related to creating and populating the array */
                                        for (AbstractInsnNode r : arrayInsnList) {
                                            il.remove(r);
                                        }

                                        /** Insert array creation and storage prior to first instruction that
                                         * pushes value onto stack for Method.invoke */
                                        AbstractInsnNode loadMethodInsn = inputs.get(0);
                                        il.insertBefore(loadMethodInsn, arrayInsns);

                                        /** Insert a load instruction for the array we created and stored */
                                        VarInsnNode loadArray = new VarInsnNode(Opcodes.ALOAD, mn.maxLocals);
                                        il.insertBefore(min, loadArray);
                                        inputs.add(loadArray);

                                        mn.maxLocals++;
                                    }

                                    if (min.getPrevious() instanceof VarInsnNode v) {

                                        /**
                                         * This is the second case, where an array reference is passed for Method.invoke.
                                         * e.g. Object[] arg = {1,2};
                                         *      m.invoke(t, arg)
                                         * <p>
                                         * The array is a memory reference, so we unpack the array values at runtime and push
                                         * them onto the stack for method invocation i.e. the equivalent of converting:
                                             Method m = Test.class.getMethod("foo");
                                             Object[] arg = {1,2}
                                         m.invoke(t, arg)
                                         *  into
                                             Object[] arg = {1,2}
                                             t.foo(arg[0], arg[1]).
                                         *  In bytecode, it would transition from this:
                                             aload_2 // load m
                                             aload_3 // load t
                                             aload_4 // load arg
                                             invokevirtual // Method java/lang/reflect/Method.invoke
                                         *  To this:
                                             aload_3 // load t
                                             aload_4 // load arg
                                             iconst_0 // at position 0
                                             aaload
                                             checkcast
                                             aload_4  // load arg
                                             iconst_1 // at position 1
                                             aaload
                                             checkcast
                                             invokestatic // Method foo
                                         */

                                        for (int i = 0; i < av.getLength(); i++) {
                                            il.insertBefore(min, new VarInsnNode(v.getOpcode(), v.var));
                                            AbstractInsnNode pos;
                                            if (i < 6) {
                                                int opcode = Opcodes.ICONST_0 + i;
                                                pos = new InsnNode(opcode);
                                            } else {
                                                pos = new IntInsnNode(Opcodes.BIPUSH, i);
                                            }
                                            il.insertBefore(min, pos);
                                            il.insertBefore(min, new InsnNode(Opcodes.AALOAD));

                                            /* Cast object value to what method is expecting */
                                            String currParam = invokeParamsType.get(i);
                                            if (currParam.length() > 1) {
                                                il.insertBefore(min, new TypeInsnNode(Opcodes.CHECKCAST,
                                                        currParam.substring(1, currParam.length() - 1)));
                                            } else {
                                                /* Dealing with primitives */
                                                List<String> wrappers = PrimitiveUtils.getWrapperFromPrimitive(
                                                        currParam.charAt(0));

                                                il.insertBefore(min, new TypeInsnNode(Opcodes.CHECKCAST,
                                                        wrappers.get(0)));

                                                il.insertBefore(min, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                                        wrappers.get(0),
                                                        wrappers.get(1).concat("Value"),
                                                        "()".concat(currParam)));
                                            }
                                        }
                                    }
                                }

                                for (AbstractInsnNode r : inputs) {
                                    if (il.contains(r)) il.remove(r);
                                }

                                boolean isStatic = isStatic(matchingMethodNode.access);

                                /* Insert bytecode equivalent of invocation */
                                MethodInsnNode invoke = new MethodInsnNode(
                                        isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
                                        mv.getClassName(),
                                        mv.getMethodName(),
                                        matchingMethodNode.desc);
                                il.set(min, invoke);


                                /* Remove instructions for Class.getMethod */
                                MethodInsnNode methodInsnNode = mv.getMethodInsnNode();
                                if (il.contains(methodInsnNode)) {
                                    for (AbstractInsnNode r : methodMap.get(methodInsnNode)) {
                                        il.remove(r);
                                    }
                                    il.remove(methodInsnNode);
                                }

                                modified = true;
                                writeEntry(reportWriter, entry);

                            } else {
                                writeEntry(untransformedWriter, entry);
                            }
                        } else {
                            writeEntry(untransformedWriter, entry);
                        }
                    }

                    else if (min.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && min.owner.equals("java/lang/Class") &&
                            (min.name.equals("getMethod") || min.name.equals("getDeclaredMethod"))) {

                        /* Get instructions to remove for getMethod or getDeclaredMethod */
                        List<AbstractInsnNode> inputs = getInputs(min, sources, il, conditionals, false);
                        List<AbstractInsnNode> remove = new ArrayList<>();
                        int start = il.indexOf(inputs.get(0));
                        int end = il.indexOf(min);
                        for (int i = start; i < end; i++) {
                            remove.add(il.get(i));
                        }

                        if (min.getNext() instanceof VarInsnNode var && var.getOpcode() == Opcodes.ASTORE) {
                            remove.add(var);
                        }

                        methodMap.put(min, remove);
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

    private void writeEntry(FileWriter untransformedWriter, TransformationEntry entry) {
        try {
            untransformedWriter.write(entry.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isInitMethod(String methodName) {
        return methodName.equals("<init>") || methodName.equals("<clinit>");
    }

    private List<AbstractInsnNode> getArrayInstructions(int start, int end, InsnList il, Map<LabelNode, LabelNode> labelMap) {
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (int i = start; i < end; i++) {
            AbstractInsnNode instruction = il.get(i);
            instructions.add(instruction);
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                labelMap.put((LabelNode) instruction, new LabelNode());
            }
        }
        return instructions;
    }

    private boolean isStatic(int modifier) {
        return (modifier & Opcodes.ACC_STATIC) != 0;
    }

    private List<BasicValue> getStackValues(int instructionIndex, Frame<BasicValue>[] frames) {
        List<BasicValue> params = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            try {
                BasicValue arg = getStackValue(instructionIndex, i, frames);
                params.add(arg);
            } catch (AnalyzerException e) {
                throw new RuntimeException(e); // TODO sonia revisit handling
            }
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

    /**
     * Under the simplifying assumption that all instructions between the
     * first instruction that pushes a value onto the stack operand and
     * the method invocation instruction, we can find all instructions
     * responsible for pushing values onto the stack operand directly and indirectly.
     * @param instr the instruction for which we want all instructions that push values onto the stack
     * @param sources the map of all indirect and direct instructions that push values onto stack
     * @param il the list of instructions for the current method node
     * @param conditionals conditional branches for conditional expressions
     * @param excludeObjectRef whether to include instructions responsible for pushing object reference.
     * @return
     */
    List<AbstractInsnNode> getInputs(AbstractInsnNode instr, Map<AbstractInsnNode, List<SourceValue>> sources,
                                     InsnList il, TreeMap<int[],AbstractInsnNode> conditionals,
                                     boolean excludeObjectRef) {

        List<AbstractInsnNode> source = new ArrayList<>();
        List<SourceValue> pending = new ArrayList<>(sources.get(instr));
        for (int pIx = 0; pIx < pending.size(); pIx++) {
            if (excludeObjectRef && pIx == 1) {
                continue;
            } else {
                SourceValue sv = pending.get(pIx);
                final boolean branch = sv.insns.size() > 1;
                for(AbstractInsnNode in: sv.insns) {
                    if(source.add(in))
                        pending.addAll(sources.getOrDefault(in, Collections.emptyList()));
                    if(branch) {
                        int ix = il.indexOf(in);
                        conditionals.forEach((b,i) -> {
                            if(b[0] <= ix && b[1] >= ix && source.add(i))
                                pending.addAll(sources.getOrDefault(i, Collections.emptyList()));
                        });
                    }
                }
            }
        }
        return source;
    }

    public interface PluginFactory {
        List<Plugin> create();
    }

    private static class DependencyPluginFactory implements PluginFactory {

        @Override
        public List<Plugin> create() {
            return List.of(PluginRepository.getPlugin("jdk-tools-jlink-internal-plugins-InheritanceGraphPlugin",
                    ModuleLayer.boot()));
        }

    }
}
