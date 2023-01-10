package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

public class MethodValue extends BasicValue {

    MethodInsnNode min;
    String className;
    String methodName;
    String desc;

    public MethodValue(MethodInsnNode min, List<? extends BasicValue> params) {
        super(Type.getReturnType((min).desc));
        this.min = min;
        if (params.get(0) instanceof ClassValue cv) {
            this.className = cv.getName();
        }
        if (params.get(1) instanceof StringValue sv) {
            this.methodName = sv.getContents();
        }
        desc = "()V"; // TODO expand this to work with non-void. 
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDesc() {
        return desc;
    }

    public MethodInsnNode getMethodInsnNode() {
        return min;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (other instanceof MethodValue v) {
            return v.desc.equals(desc)
                    && v.className.equals(className)
                    && v.methodName.equals(methodName) && v.min.equals(min);
        }
        return false;
    }
}
