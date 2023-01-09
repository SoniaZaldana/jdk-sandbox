
package mypackage;

import java.lang.reflect.*;

public class ClassGetMethodTest extends Super {

    public static void main (String[] args) throws Exception {

        /* Tests involving parameters for Method.invoke */
        testInvocationWithObjectRef();
        testInvocationCreatingObjectInCall();

        /* Tests involving parameters for Class.getMethod */
        testNoParameterCall();
        testNoParameterCallWithEmptyArray();
        testGetMethodWithImplicitArray();
        testTransformationUnachievableLackOfConstantClassName();
        testTransformationUnachievableLackOfConstantMethodName();
        testTransformationUnachievableLackOfConstantParams();

        /* Void method tests */
        testAllPrimitivesWithArrayRef();
        testAllPrimitivesWithoutArrayRef();
        testAllPrimitivesWithImplicitArray();

        testAllObjectWithArrayRef();
        testAllObjectWithoutArrayRef();
        testAllObjectsWithImplicitArray();
        testAllObjectsWithoutArrayRefAndVariousWaysToPopulateArguments();


        testMixedPrimitiveAndObjectRefWithArrayRef();
        testMixedPrimitivesAndObjectRefWithoutArrayRef();
        testMixedPrimitivesAndObjectRefWithImplicitArray();

        /* Non-void method tests */

        /* Inheritance lookup tests */
        testTransformationInheritanceLookup();
        try {
            testTransformationUnachievableDueToInexistentMethod();
            testTransformationUnachievableDueToPrivateMethod();
            assert false : "Reflection handling should've" +
                    " thrown exception for the above";
        } catch (Exception e) {
            // continue
        }
    }

    public static void testInvocationWithObjectRef() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("noParam");
        ClassGetMethodTest t = new ClassGetMethodTest();
        m.invoke(t);
    }

    public static void testInvocationCreatingObjectInCall() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("noParam");
        m.invoke(new ClassGetMethodTest());
    }

    public static void testNoParameterCall() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("noParam");
        m.invoke(new ClassGetMethodTest());
    }

    public static void testNoParameterCallWithEmptyArray() throws Exception {
        Class[] arg = {};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("noParam", arg);
        m.invoke(new ClassGetMethodTest());
    }

    public static void testGetMethodWithImplicitArray() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allObjectRef",
                ClassGetMethodTest.class, ClassGetMethodTest.class);
        Object[] carg = {new ClassGetMethodTest(), new ClassGetMethodTest()};
        m.invoke(new ClassGetMethodTest(), carg);
    }

    public static void testAllPrimitivesWithoutArrayRef() throws Exception {
        Class[] arg = {int.class, long.class,
                short.class, float.class,
                char.class, double.class, byte.class, boolean.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allPrimitives", arg);
        m.invoke(new ClassGetMethodTest(), new Object[] {0, 1L, (short) 2, 3F, 'H', 4D, (byte) 0, true});
    }

    public static void testAllPrimitivesWithImplicitArray() throws Exception {
        Class[] arg = {int.class, long.class,
                short.class, float.class,
                char.class, double.class, byte.class, boolean.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allPrimitives", arg);
        m.invoke(new ClassGetMethodTest(),0, 1L, (short) 2, 3F, 'H', 4D, (byte) 0, true);
    }

    public static void testAllPrimitivesWithArrayRef() throws Exception {
        Class[] arg = {int.class, long.class,
                short.class, float.class,
                char.class, double.class, byte.class, boolean.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allPrimitives", arg);
        Object[] carg = {0, 1L, (short) 2, 3F, 'H', 4D, (byte) 0, true};
        m.invoke(new ClassGetMethodTest(), carg);
    }

    public static void testAllObjectWithArrayRef() throws Exception {
        Class[] arg = {mypackage.ClassGetMethodTest.class, mypackage.ClassGetMethodTest.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allObjectRef", arg);
        Object[] carg = {new ClassGetMethodTest(), new ClassGetMethodTest()};
        m.invoke(new ClassGetMethodTest(), carg);
    }

    public static void testAllObjectWithoutArrayRef() throws Exception {
        Class[] arg = {mypackage.ClassGetMethodTest.class, mypackage.ClassGetMethodTest.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allObjectRef", arg);
        m.invoke(new ClassGetMethodTest(), new Object[] {new ClassGetMethodTest(), new ClassGetMethodTest()});
    }

    public static void testAllObjectsWithImplicitArray() throws Exception {
        Class[] arg = {mypackage.ClassGetMethodTest.class, mypackage.ClassGetMethodTest.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allObjectRef", arg);
        m.invoke(new ClassGetMethodTest(), new ClassGetMethodTest(), new ClassGetMethodTest());
    }

    public static void testAllObjectsWithoutArrayRefAndVariousWaysToPopulateArguments() throws Exception {
        Class[] arg = {mypackage.ClassGetMethodTest.class, mypackage.ClassGetMethodTest.class,
                mypackage.ClassGetMethodTest.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allObjectRefMoreParam", arg);
        ClassGetMethodTest t = new ClassGetMethodTest();
        m.invoke(new ClassGetMethodTest(), getObjectRef(new Object[] {1,2}),
                getTest(getObjectRef(new Object[] {1,2, 3})), t);

    }

    public static void testMixedPrimitiveAndObjectRefWithArrayRef() throws Exception {
        Class[] arg = {double.class, mypackage.ClassGetMethodTest.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("mixedPrimitiveAndObjectRef", arg);
        Object[] carg = {1D, new ClassGetMethodTest()};
        m.invoke(new ClassGetMethodTest(), carg);
    }

    public static void testMixedPrimitivesAndObjectRefWithoutArrayRef() throws Exception {
        Class[] arg = {double.class, mypackage.ClassGetMethodTest.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("mixedPrimitiveAndObjectRef", arg);
        m.invoke(new ClassGetMethodTest(), new Object[] {1D, new ClassGetMethodTest()});
    }

    public static void testMixedPrimitivesAndObjectRefWithImplicitArray() throws Exception {
        Class[] arg = {double.class, mypackage.ClassGetMethodTest.class};
        Method m = mypackage.ClassGetMethodTest.class.getMethod("mixedPrimitiveAndObjectRef", arg);
        m.invoke(new ClassGetMethodTest(), 1D, new ClassGetMethodTest());
    }

    public static void testTransformationUnachievableLackOfConstantClassName() throws Exception {
        Method m = getClassName().getMethod("noParam");
        ClassGetMethodTest t = new ClassGetMethodTest();
        Object[] carg = {};
        m.invoke(t, carg);
    }

    public static void testTransformationUnachievableLackOfConstantMethodName() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod(getMethodName());
        ClassGetMethodTest t = new ClassGetMethodTest();
        Object[] carg = {};
        m.invoke(t, carg);
    }

    public static void testTransformationUnachievableLackOfConstantParams() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("allObjectRef", getParams());
        Object[] carg = {new ClassGetMethodTest(), new ClassGetMethodTest()};
        m.invoke(new ClassGetMethodTest(), carg);
    }

    public static void testTransformationInheritanceLookup() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("parentMethod");
        m.invoke(new ClassGetMethodTest());
        m = mypackage.ClassGetMethodTest.class.getMethod("hashCode");
        m.invoke(new ClassGetMethodTest());
    }

    public static void testTransformationUnachievableDueToInexistentMethod() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("inexistentParentMethod");
        m.invoke(new ClassGetMethodTest());
    }

    public static void testTransformationUnachievableDueToPrivateMethod() throws Exception {
        Method m = mypackage.ClassGetMethodTest.class.getMethod("privateParentMethod");
        m.invoke(new ClassGetMethodTest());
    }

    /** Helpers below */ 

    public static void allPrimitives(int i, long l,
                                     short s, float f,
                                     char c, double d, byte b, boolean bool) {
        System.out.println("success");
    }

    public static void allObjectRef(ClassGetMethodTest t1, ClassGetMethodTest t2) {
        System.out.println("success");
    }

    public static void allObjectRefMoreParam(ClassGetMethodTest t1, ClassGetMethodTest t2, ClassGetMethodTest t3) {
        System.out.println("success");
    }

    public static void noParam() {
        System.out.println("success");
    }

    public static void mixedPrimitiveAndObjectRef(double d, ClassGetMethodTest t) {
        System.out.println("success");
    }

    public static Class<?> getClassName() {
        return mypackage.ClassGetMethodTest.class;
    }

    public static String getMethodName() {
        return "noParam";
    }

    public static Class[] getParams() {
        return new Class[] {mypackage.ClassGetMethodTest.class, mypackage.ClassGetMethodTest.class};
    }

    public static ClassGetMethodTest getObjectRef(Object[] arr) {
        return new ClassGetMethodTest();
    }

    public static ClassGetMethodTest getTest(ClassGetMethodTest t) {
        return t;
    }

}