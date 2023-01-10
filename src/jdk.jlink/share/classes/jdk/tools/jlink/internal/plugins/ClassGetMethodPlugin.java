package jdk.tools.jlink.internal.plugins;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;
import jdk.internal.org.objectweb.asm.tree.analysis.Frame;
import jdk.tools.jlink.internal.ConstantInterpreter;
import jdk.tools.jlink.internal.MethodValue;
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
        // TODO once i figure out exact config options
    }


    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        Objects.requireNonNull(in);
        Objects.requireNonNull(out);

        in.entries()
                .forEach(resource -> {
                    String path = resource.path();
                    // TODO remove java.base when finished testing
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
            int instructionIndex = 0;
            Frame<BasicValue>[] frames = analyzer.getFrames();
            ListIterator<AbstractInsnNode> iterator = il.iterator();
            while (iterator.hasNext()) {
                AbstractInsnNode insn = iterator.next();
                if (insn instanceof MethodInsnNode min) {
                    if (min.getOpcode() == Opcodes.INVOKEVIRTUAL && min.name.equals("invoke")
                            && min.owner.equals("java/lang/reflect/Method")) {


                        List<BasicValue> params = getParameters(instructionIndex, frames);
                        if (params.get(0) instanceof MethodValue mv) {

                            /* Insert bytecode equivalent of invocation */
                            MethodInsnNode invoke = new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    mv.getClassName(),
                                    mv.getMethodName(),
                                    mv.getDesc());
                            il.set(min, invoke);

                            il.remove(mv.getMethodInsnNode());

                            modified = true;
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
                throw new RuntimeException(e); // TODO revisit this handling
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

}
