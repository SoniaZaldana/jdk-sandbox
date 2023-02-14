package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.ArrayList;
import java.util.List;

public class MethodValue extends BasicValue {

    MethodInsnNode min;
    String className;
    String methodName;
    String desc;
    List<String> splitDesc;
    boolean isDeclared;

    public MethodValue(MethodInsnNode min, ClassValue cv, StringValue sv, ArrayValue av, boolean isDeclared) {
        super(Type.getReturnType((min).desc));
        this.min = min;
        this.splitDesc = new ArrayList<>();
        this.className = cv.getName();
        this.methodName = sv.getContents();

        BasicValue[] methodParams = av.values;
        String types = "(";
        for (BasicValue param : methodParams) {
            if (param instanceof ClassValue paramValue) {
                String append = Type.getObjectType(paramValue.getName()).toString();
                splitDesc.add(append);
                types = types.concat(Type.getObjectType(paramValue.getName()).toString());
            } else if (param instanceof FieldValue fv) {
                if (fv.node.desc.equals("Ljava/lang/Class;") && fv.node.name.equals("TYPE")) {
                    String type = PrimitiveUtils.getPrimitiveFromWrapper(fv.owner);
                    splitDesc.add(type);
                    types = types.concat(type);
                }
            }
        }
        types = types.concat(")");
        desc = types;
        this.isDeclared = isDeclared;
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

    public List<String> getSplitDesc() {
        return splitDesc;
    }

    public MethodInsnNode getMethodInsnNode() {
        return min;
    }

    public boolean isDeclared() {
        return this.isDeclared;
    }

    @Override
    public String toString()  {
        return String.format("Method - %s#%s%s", className, methodName, desc);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (other instanceof MethodValue v) {
            return v.desc.equals(desc)
                    && v.className.equals(className)
                    && v.methodName.equals(methodName)
                    && v.min.equals(min)
                    && v.splitDesc.equals(splitDesc)
                    && v.isDeclared == isDeclared;
        }
        return false;
    }
}
