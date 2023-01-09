package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.TypeInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;


public class ArrayValue extends BasicValue {

    int length;
    BasicValue[] values;
    TypeInsnNode insn;
    String desc;
    public ArrayValue(TypeInsnNode insn, IntValue value) {
        super(Type.getType("[" + Type.getObjectType((insn).desc)));
        this.length = value.getValue();
        this.values = new BasicValue[length];
        this.insn = insn;
        this.desc = insn.desc;
    }

    public ArrayValue(ArrayValue av) {
        super(Type.getType("[" + Type.getObjectType((av).desc)));
        this.length = av.getLength();
        this.values = av.values;
        this.desc = av.desc;
        this.insn = av.insn;
    }


    public int getLength() {
        return length;
    }

    public void addEntry(IntValue i, BasicValue value) {
        values[i.value] = value;
    }

    public TypeInsnNode getInsn() {
        return this.insn;
    }

    @Override
    public String toString() {
        String contents = "[";
        for (int i = 0; i < values.length; i++) {
            contents += values[i].toString();
            if (i != values.length - 1) {
                contents += ", ";
            }
        }
        contents += "]";
        return contents;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (other instanceof ArrayValue v) {
            return v.length == length && v.values.equals(values)
                    && v.insn.equals(insn) && v.desc.equals(desc);
        }
        return false;
    }
}
