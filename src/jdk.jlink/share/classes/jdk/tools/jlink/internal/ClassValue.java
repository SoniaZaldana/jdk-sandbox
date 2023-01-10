package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

public class ClassValue extends BasicValue {
    private String name;

    public ClassValue(String name) {
        super(Type.getObjectType("java/lang/Class"));
        this.name = name;
    }

    public ClassValue(ClassValue v) {
        super(Type.getObjectType("java/lang/Class"));
        this.name = new String(v.getName());
    }

    public String getName() {
        return name;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (o instanceof ClassValue) {
            String oname = ((ClassValue)o).name;
            return (name == null && oname == null) || (oname != null && oname.equals(name));
        }
        return false;
    }
}
