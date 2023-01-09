package jdk.tools.jlink.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrimitiveUtils {

    private static Map<String, String> wrapperToPrimitive;
    private static Map<Character, List<String>> primitiveToWrapper;

    static {
        populateWrapperToPrimitive();
        populatePrimitiveToWrapper();
    }

    private static void populateWrapperToPrimitive() {
        wrapperToPrimitive = new HashMap<>();
        wrapperToPrimitive.put("java/lang/Integer", "I");
        wrapperToPrimitive.put("java/lang/Double", "D");
        wrapperToPrimitive.put("java/lang/Short", "S");
        wrapperToPrimitive.put("java/lang/Character", "C");
        wrapperToPrimitive.put("java/lang/Byte", "B");
        wrapperToPrimitive.put("java/lang/Boolean", "Z");
        wrapperToPrimitive.put("java/lang/Float", "F");
        wrapperToPrimitive.put("java/lang/Long", "J");
    }

    private static void populatePrimitiveToWrapper() {
        primitiveToWrapper = new HashMap<>();
        primitiveToWrapper.put('I', Arrays.asList("java/lang/Integer", "int"));
        primitiveToWrapper.put('D', Arrays.asList("java/lang/Double", "double"));
        primitiveToWrapper.put('B', Arrays.asList("java/lang/Byte", "byte"));
        primitiveToWrapper.put('Z', Arrays.asList("java/lang/Boolean", "boolean"));
        primitiveToWrapper.put('F', Arrays.asList("java/lang/Float", "float"));
        primitiveToWrapper.put('J', Arrays.asList("java/lang/Long", "long"));
        primitiveToWrapper.put('S', Arrays.asList("java/lang/Short", "short"));
        primitiveToWrapper.put('C', Arrays.asList("java/lang/Character", "char"));

    }

    public static String getPrimitiveFromWrapper(String className) {
        return wrapperToPrimitive.get(className);
    }

    public static List<String> getWrapperFromPrimitive(Character c) {
        return primitiveToWrapper.get(c);
    }

    public static boolean isPrimitive(String className) {
        return primitiveToWrapper.keySet().contains(className);
    }
}
