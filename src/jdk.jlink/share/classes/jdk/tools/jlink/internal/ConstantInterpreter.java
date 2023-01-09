package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

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
        } else if (insn instanceof InsnNode || insn instanceof IntInsnNode) {
            switch (insn.getOpcode()) {
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case BIPUSH:
                case SIPUSH:
                    return new IntValue(insn);
            }
        }
        else if (insn instanceof FieldInsnNode field) {
            if (field.desc.equals("Ljava/lang/Class;") && field.name.equals("TYPE")) {
                return new FieldValue(field);
            }
        }

        return super.newOperation(insn);
    }

    @Override
    public BasicValue naryOperation(
            final AbstractInsnNode insn, final List<? extends BasicValue> values)
            throws AnalyzerException {
        if (insn instanceof MethodInsnNode min) {
            if (min.getOpcode() == Opcodes.INVOKEVIRTUAL && min.name.equals("getMethod")
                    && min.owner.equals("java/lang/Class")) {
                if (values.get(0) instanceof ClassValue cv
                        && values.get(1) instanceof StringValue sv
                        && values.get(2) instanceof ArrayValue av) {
                    return new MethodValue(min, cv, sv, av);
                }
            }
        }

        return super.naryOperation(insn, values);
    }

    @Override
    public BasicValue unaryOperation(final AbstractInsnNode insn, final BasicValue value)
            throws AnalyzerException {
        if (insn instanceof TypeInsnNode tn && insn.getOpcode() == Opcodes.ANEWARRAY
                && value instanceof IntValue i) {
            return new ArrayValue(tn, i);
        }
        return super.unaryOperation(insn, value);
    }

    @Override
    public BasicValue ternaryOperation(
            final AbstractInsnNode insn,
            final BasicValue value1,
            final BasicValue value2,
            final BasicValue value3)
            throws AnalyzerException {

        if (insn.getOpcode() == Opcodes.AASTORE) {
            if (value1 instanceof ArrayValue array
                    && value2 instanceof IntValue i) {
                try {
                    array.addEntry(i, value3);
                } catch (IndexOutOfBoundsException e) {
                    // just catch so component parts handle exception.
                }
            }
        }

        return super.ternaryOperation(insn, value1, value2, value3);
    }

    @Override
    public BasicValue merge(BasicValue v1, BasicValue v2) {
        if (v1 instanceof StringValue
                && v2 instanceof StringValue
                && v1.equals(v2)) {
            return new StringValue((StringValue)v1);
        } else if (v1 instanceof IntValue
                && v2 instanceof IntValue
                && v1.equals(v2)) {
            return new IntValue((IntValue) v1);
        } else if (v1 instanceof ClassValue
                && v2 instanceof ClassValue
                && v1.equals(v2)) {
            return new ClassValue((ClassValue) v1);
        } else if (v1 instanceof FieldValue
                && v2 instanceof FieldValue
                && v1.equals(v2)) {
            return new FieldValue((FieldValue) v1);
        } else if (v1 instanceof ArrayValue
                && v2 instanceof ArrayValue
                && v1.equals(v2)) {
            return new ArrayValue((ArrayValue) v1);
        }
        return super.merge(degradeValue(v1), degradeValue(v2));
    }

    private BasicValue degradeValue(BasicValue v) {
        if (v instanceof StringValue || v instanceof BooleanValue
                || v instanceof IntValue || v instanceof ClassValue
                || v instanceof FieldValue || v instanceof ArrayValue
                || v instanceof MethodValue) {
            return REFERENCE_VALUE;
        }
        return v;
    }
}
