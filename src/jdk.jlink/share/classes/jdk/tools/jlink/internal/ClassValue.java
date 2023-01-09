package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

public class ClassValue extends BasicValue {
    private String name;
    private LdcInsnNode ldc;

    public ClassValue(String name, LdcInsnNode ldc) {
        super(Type.getObjectType("java/lang/Class"));
        this.name = name;
        this.ldc = ldc;
    }

    public ClassValue(ClassValue v) {
        super(Type.getObjectType("java/lang/Class"));
        this.name = new String(v.getName());
    }

    public String getName() {
        return name;
    }

    public LdcInsnNode getLdc() {
        return ldc;
    } // TODO do we need this? 

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (o instanceof ClassValue) {
            String oname = ((ClassValue)o).name;
            LdcInsnNode oldc = ((ClassValue)o).ldc;
            // TODO also add ldc to first condition?
            return (name == null && oname == null) || (oname != null && oname.equals(name) && oldc.equals(ldc));
        }
        return false;
    }
}
