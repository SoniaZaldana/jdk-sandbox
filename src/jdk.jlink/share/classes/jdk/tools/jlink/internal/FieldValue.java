package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.FieldInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

public class FieldValue extends BasicValue {

    FieldInsnNode node;
    String owner;

    public FieldValue(FieldInsnNode node) {
        super(Type.getType(node.desc));
        this.node = node;
        this.owner = node.owner;
    }

    public FieldValue(FieldValue fv) {
        super(fv.getType());
        this.node = fv.getNode();
        this.owner = fv.owner;
    }

    public FieldInsnNode getNode() {
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o instanceof FieldValue f) {
            return f.node.equals(node) && f.owner.equals(((FieldValue) o).owner);
        }
        return false;
    }
}
