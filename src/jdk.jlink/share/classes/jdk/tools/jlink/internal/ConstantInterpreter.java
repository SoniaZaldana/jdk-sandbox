package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

import static jdk.internal.org.objectweb.asm.tree.analysis.BasicValue.REFERENCE_VALUE;

public class ConstantInterpreter extends BasicInterpreter {

    public ConstantInterpreter() {
        super(ASM9);
    }

    @Override
    public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException
    {
        if (insn instanceof LdcInsnNode) {
            LdcInsnNode ldc = (LdcInsnNode)insn;
            Object cst = ldc.cst;
            if (cst instanceof String) {
                return new StringValue((String)cst, ldc);
            } else if (cst instanceof Type c) {
                return new ClassValue(c.getInternalName());
            }
        } else if (insn instanceof InsnNode) {
            InsnNode i = (InsnNode)  insn;
            if (i.getOpcode() == Opcodes.ICONST_0) {
                return new BooleanValue(false);
            } else if (i.getOpcode() == Opcodes.ICONST_1) {
                return new BooleanValue(true);
            }
        }
        return super.newOperation(insn);
    }

    @Override
    public BasicValue merge(BasicValue v1, BasicValue v2) {
        if (v1 instanceof StringValue
                && v2 instanceof StringValue
                && v1.equals(v2)) {
            return new StringValue((StringValue)v1);
        }
        return super.merge(degradeValue(v1), degradeValue(v2));
    }

    private BasicValue degradeValue(BasicValue v) {
        if (v instanceof StringValue) {
            return REFERENCE_VALUE;
        } else if (v instanceof BooleanValue) {
            return REFERENCE_VALUE;
        }
        return v;
    }
}
