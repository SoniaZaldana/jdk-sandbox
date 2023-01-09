import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.test.lib.compiler.CompilerUtils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.util.FileUtils;
import jdk.tools.jlink.internal.ResourcePoolManager;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static jdk.internal.org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.process.ProcessTools.executeProcess;

/**
 * @test
 * @summary Test the --class-get-method plugin
 * @author Sonia Zaldana Calles
 * @library /test/lib
 * @compile ClassGetMethodPluginTest.java
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          java.base/jdk.internal.org.objectweb.asm.tree.analysis
 * @run testng/othervm -ea -esa ClassGetMethodPluginTest
 */
public class ClassGetMethodPluginTest {

    private final String PLUGIN_NAME = "class-get-method";
    private static final String MODULE_NAME = "mymodule";
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path IMAGE = Paths.get("image");
    private static final Path EXTRACT = Paths.get("extract");
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String MAIN_MID = "mymodule/mypackage.ClassGetMethodTest";
    static final String MODULE_PATH = Paths.get(JAVA_HOME, "modsPath").toString()
            + File.pathSeparator + MODS_DIR.toString();

    private final List<String> transformedMethods = Arrays.asList(
            "testInvocationWithObjectRef",
            "testInvocationCreatingObjectInCall",
            "testNoParameterCall",
            "testNoParameterCallWithEmptyArray",
            "testGetMethodWithImplicitArray",
            "testAllPrimitivesWithoutArrayRef",
            "testAllPrimitivesWithImplicitArray",
            "testAllPrimitivesWithArrayRef",
            "testAllObjectWithArrayRef",
            "testAllObjectWithoutArrayRef",
            "testAllObjectsWithImplicitArray",
            "testAllObjectsWithoutArrayRefAndVariousWaysToPopulateArguments",
            "testMixedPrimitiveAndObjectRefWithArrayRef",
            "testMixedPrimitivesAndObjectRefWithoutArrayRef",
            "testMixedPrimitivesAndObjectRefWithImplicitArray",
            "testTransformationInheritanceLookup");

    @BeforeTest
    public void setup() throws Throwable {
        Path moduleSource = SRC_DIR.resolve(MODULE_NAME);
        assertTrue(CompilerUtils.compile(moduleSource, MODS_DIR,
                "--module-source-path", SRC_DIR.toString(),
                "--add-exports", "java.base/jdk.internal.module=" + MODULE_NAME,
                "--add-exports", "java.base/jdk.internal.org.objectweb.asm=" + MODULE_NAME));

        if (Files.exists(IMAGE) || Files.exists(EXTRACT)) {
            throw new AssertionError("Directories should have been cleaned up in tear down");
        }

        createImage(IMAGE, MODULE_NAME);

        Path modules = IMAGE.resolve("lib").resolve("modules");
        assertTrue(executeProcess("jimage", EXTRACT.toString(),
                "--dir", EXTRACT.toString(), modules.toString())
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue() == 0);
    }

    @Test
    public void testRunTransformedClass() throws Throwable {
        Path java = IMAGE.resolve("bin").resolve("java");
        assertTrue(executeProcess(java.toString(),
                "-m", MAIN_MID)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue() == 0);
    }

    @Test
    public void testTransformationsPresent() throws Throwable {
        Path path = EXTRACT.resolve("mymodule").resolve("mypackage").resolve("ClassGetMethodTest.class");
        assertTrue(executeProcess("javap", "-c", "-verbose",
                path.toString())
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue() == 0); // This is in place for debugging.

        byte[] arr = Files.readAllBytes(path);
        ResourcePoolManager resourcesMgr = new ResourcePoolManager();
        ResourcePoolEntry resource = ResourcePoolEntry.create("/" + path, arr);
        resourcesMgr.add(resource);

        resourcesMgr.resourcePool().entries()
                .forEach(r -> {
                    if (r.path().endsWith("ClassGetMethodTest.class")) {
                        byte[] inBytes = r.contentBytes();
                        ClassReader cr = new ClassReader(inBytes);
                        ClassNode cn = new ClassNode();
                        cr.accept(cn, SKIP_FRAMES);

                        for (MethodNode mn : cn.methods) {
                            if (transformedMethods.contains(mn.name)) {
                                if (containsReflectiveOperations(mn)) {
                                    throw new AssertionError("Method " + mn.name + " should be transformed");
                                }
                            }
                        }

                    }
                });
    }

    @AfterTest
    public void tearDown() {
        if (Files.exists(IMAGE)) FileUtils.deleteFileTreeUnchecked(IMAGE);
        if (Files.exists(EXTRACT)) FileUtils.deleteFileTreeUnchecked(EXTRACT);
    }

    private boolean containsReflectiveOperations(MethodNode mn) {
        for (AbstractInsnNode i : mn.instructions) {
            if (i instanceof MethodInsnNode min) {
                if (min.name.equals("invoke") && min.owner.equals("java/lang/reflect/Method")) {
                    return true;
                }
            }
        }
        return false;
    }


    private void createImage(Path outputDir, String... modules) throws Throwable {
        assertTrue(JLink.JLINK_TOOL.run(System.out, System.out,
                "--output", outputDir.toString(),
                "--add-modules", Arrays.stream(modules).collect(Collectors.joining(",")),
                "--module-path", MODULE_PATH,
                "--class-get-method") == 0); // TODO check this
    }

    static class JLink {
        static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
                .orElseThrow(() ->
                        new RuntimeException("jlink tool not found")
                );

        static JLink run(String... options) {
            JLink jlink = new JLink();
            if (jlink.execute(options) != 0) {
                throw new AssertionError("Jlink expected to exit with 0 return code");
            }
            return jlink;
        }

        final List<String> output = new ArrayList<>();
        private int execute(String... options) {
            System.out.println("jlink " +
                    Stream.of(options).collect(Collectors.joining(" ")));

            StringWriter writer = new StringWriter();
            PrintWriter pw = new PrintWriter(writer);
            int rc = JLINK_TOOL.run(pw, pw, options);
            System.out.println(writer.toString());
            Stream.of(writer.toString().split("\\v"))
                    .map(String::trim)
                    .forEach(output::add);
            return rc;
        }

        List<String> output() {
            return output;
        }
    }

}