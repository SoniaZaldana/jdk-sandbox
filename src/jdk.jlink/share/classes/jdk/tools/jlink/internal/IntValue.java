package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.IntInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

public class IntValue extends BasicValue {

    int value;
    AbstractInsnNode insn;
    public IntValue(AbstractInsnNode insn) {
        super(Type.INT_TYPE);
        this.insn = insn;
        int opcode = insn.getOpcode();
        value = calculateValue(opcode);
    }

    public IntValue(IntValue i) {
        super(Type.INT_TYPE);
        this.insn = i.getInsn();
        this.value = i.getValue();
    }

    private int calculateValue(int opcode) {
        switch (opcode) {
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                return opcode - 3;
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return ((IntInsnNode) insn).operand;
        }
        return -1;
    }

    public int getValue() {
        return value;
    }

    public AbstractInsnNode getInsn() {
        return insn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (o instanceof IntValue i) {
            return i.value == value;
        }
        return false;
    }
}
