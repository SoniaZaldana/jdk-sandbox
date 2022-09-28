/*
 * Copyright (c) 2016, 2022 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.tools.jlink.internal.plugins;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jdk.tools.jlink.internal.PluginRepository;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolModule;
import jdk.tools.jlink.plugin.*;
import jdk.internal.org.objectweb.asm.ClassReader;
import static jdk.internal.org.objectweb.asm.ClassReader.*;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.LineNumberNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;

public final class ClassForNamePlugin implements Plugin {
    public static final String NAME = "class-for-name";
    private static final String GLOBAL = "global";
    private static final String MODULE = "module";
    private boolean isGlobalTransformation;
    private final DependencyPluginFactory factory;


    public ClassForNamePlugin() {
        this.factory = new DependencyPluginFactory();
    }

    private static String binaryClassName(String path) {
        return path.substring(path.indexOf('/', 1) + 1,
                path.length() - ".class".length());
    }

    private static int getAccess(ResourcePoolEntry resource) {
        ClassReader cr = new ClassReader(resource.contentBytes());

        return cr.getAccess();
    }

    private static String getPackage(String binaryName) {
        int index = binaryName.lastIndexOf("/");

        return index == -1 ? "" : binaryName.substring(0, index);
    }

    @Override
    public List<Plugin> requiredPlugins() {
        return factory.create();
    }

    private void modifyInstructions(LdcInsnNode ldc, InsnList il, MethodInsnNode min, String thatClassName) {
        Type type = Type.getObjectType(thatClassName);
        MethodInsnNode lookupInsn = new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/invoke/MethodHandles",
                "lookup","()Ljava/lang/invoke/MethodHandles$Lookup;");
        LdcInsnNode ldcInsn = new LdcInsnNode(type);
        MethodInsnNode ensureInitializedInsn = new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandles$Lookup",
                "ensureInitialized", "(Ljava/lang/Class;)Ljava/lang/Class;");

        il.remove(ldc);
        il.set(min, lookupInsn);
        il.insert(lookupInsn, ldcInsn);
        il.insert(ldcInsn, ensureInitializedInsn);
    }

    private ResourcePoolEntry transform(ResourcePoolEntry resource, ResourcePool pool) {
        byte[] inBytes = resource.contentBytes();
        ClassReader cr = new ClassReader(inBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, EXPAND_FRAMES);
        List<MethodNode> ms = cn.methods;
        boolean modified = false;
        LdcInsnNode ldc = null;

        String thisPackage = getPackage(binaryClassName(resource.path()));

        for (MethodNode mn : ms) {
            InsnList il = mn.instructions;
            Iterator<AbstractInsnNode> it = il.iterator();

            while (it.hasNext()) {
                AbstractInsnNode insn = it.next();

                if (insn instanceof LdcInsnNode) {
                    ldc = (LdcInsnNode)insn;
                } else if (insn instanceof MethodInsnNode && ldc != null) {
                    MethodInsnNode min = (MethodInsnNode)insn;

                    if (min.getOpcode() == Opcodes.INVOKESTATIC &&
                            min.name.equals("forName") &&
                            min.owner.equals("java/lang/Class") &&
                            min.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                        String ldcClassName = ldc.cst.toString();
                        String thatClassName = ldcClassName.replaceAll("\\.", "/");

                        if (isGlobalTransformation) {
                            /* Blindly transform bytecode */
                            modifyInstructions(ldc, il, min, thatClassName);
                            modified = true;
                        } else {
                            /* Transform calls for classes within the same module */
                            Optional<ResourcePoolEntry> thatClass =
                                    pool.findEntryInContext(thatClassName + ".class", resource);

                            if (thatClass.isPresent()) {
                                int thatAccess = getAccess(thatClass.get());
                                String thatPackage = getPackage(thatClassName);

                                if ((thatAccess & Opcodes.ACC_PRIVATE) != Opcodes.ACC_PRIVATE &&
                                        ((thatAccess & Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC ||
                                                thisPackage.equals(thatPackage))) {
                                    modifyInstructions(ldc, il, min, thatClassName);
                                    modified = true;

                                }
                            } else {
                                /* Check module graph to see if class is accessible */
                                ResourcePoolModule targetModule = getTargetModule(pool, thatClassName);
                                if (targetModule != null
                                        && ModuleGraphPlugin.isAccessible(thatClassName,
                                        resource.moduleName(), targetModule.name())) {
                                    modifyInstructions(ldc, il, min, thatClassName);
                                    modified = true;
                                }
                            }
                        }
                    }
                    ldc = null;
                } else if (!(insn instanceof LabelNode) &&
                        !(insn instanceof LineNumberNode)) {
                    ldc = null;
                }

            }
        }

        if (modified) {
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            byte[] outBytes = cw.toByteArray();

            return resource.copyWithContent(outBytes);
        }

        return resource;
    }

    private ResourcePoolModule getTargetModule(ResourcePool pool, String givenClass) {
        ResourcePoolModule targetModule = pool.moduleView().modules()
                .filter(m -> m.findEntry(givenClass.replace(".", "/") + ".class").isPresent())
                .findFirst().orElse(null);
        return targetModule;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        Objects.requireNonNull(in);
        Objects.requireNonNull(out);

        in.entries()
                .forEach(resource -> {
                    String path = resource.path();

                    if (path.endsWith(".class") && !path.endsWith("/module-info.class")) {
                        out.add(transform(resource, in));
                    } else {
                        out.add(resource);
                    }
                });
        return out.build();
    }
    @Override
    public Category getType() {
        return Category.TRANSFORMER;
    }
    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public String getArgumentsDescription() {
        return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public void configure(Map<String, String> config) {
        String arg = config.get(getName());
        if (arg != null) {
            if (arg.equalsIgnoreCase(GLOBAL)) {
                isGlobalTransformation = true;
            } else if (! arg.equalsIgnoreCase(MODULE)){
                throw new IllegalArgumentException(getName() + ": " + arg);
            }
        }
    }

    public interface PluginFactory {
        List<Plugin> create();
    }

    private static class DependencyPluginFactory implements PluginFactory {

        @Override
        public List<Plugin> create() {
            return List.of(PluginRepository.getPlugin("jdk-tools-jlink-internal-plugins-ModuleGraphPlugin",
                    ModuleLayer.boot()));
        }

    }
}
