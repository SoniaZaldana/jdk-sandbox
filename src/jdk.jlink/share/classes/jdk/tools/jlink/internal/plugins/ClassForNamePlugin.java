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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import jdk.internal.org.objectweb.asm.tree.JumpInsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.LineNumberNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;

public final class ClassForNamePlugin implements Plugin {
    public static final String NAME = "class-for-name";
    private static final String GLOBAL = "global";
    private static final String MODULE = "module";
    private boolean isGlobalTransformation;
    private final DependencyPluginFactory factory;
    private Map<Integer, Integer> classForNameIndexMap = new HashMap<>();
    private final List<String> handledExceptions = new ArrayList<>(Arrays.asList("java/lang/ClassNotFoundException"));

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
        int index = il.indexOf(ldc);
        classForNameIndexMap.put(index, index);
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

    /**
     * Removes exception handlers that are no longer reachable based on Class.forName call transformations
     * @param mn the current method node
     * @param il the instruction list for the current method node
     */
    private void removeUnreachableExceptionHandlers(MethodNode mn, InsnList il) {


        if (! mn.tryCatchBlocks.isEmpty()) {
            Map<TryCatchBlockNode, List<AbstractInsnNode>> blocksToRemove = new HashMap<>();
            Map<TryCatchBlockNode, List<AbstractInsnNode>> instructionMap = populateCatchInstructionMap(
                    mn.tryCatchBlocks, il);

            if (! instructionMap.isEmpty()) {
                for (TryCatchBlockNode tryCatch : mn.tryCatchBlocks) {
                    int start = il.indexOf(tryCatch.start);
                    int end = il.indexOf(tryCatch.end);
                    List<Integer> callsWrappedByHandler = getIndicesInRange(start, end, classForNameIndexMap);
                    if (! callsWrappedByHandler.isEmpty() && allCallsTransformed(start, end, tryCatch.start, mn.tryCatchBlocks, il)
                            && handledExceptions.contains(tryCatch.type)) {

                        /* Remove the calls wrapped by this handler from map */
                        for (Integer index : callsWrappedByHandler) {
                            classForNameIndexMap.remove(index);
                        }

                        blocksToRemove.put(tryCatch, instructionMap.get(tryCatch));

                        /* At this point, we might have over estimations of instruction removal when dealing
                           with multiple catch blocks or finally blocks. We don't want to remove finally blocks
                           or catch blocks handling other types of exceptions, so we remedy that. */


                        for (TryCatchBlockNode otherCatch : mn.tryCatchBlocks) {
                            if (! tryCatch.equals(otherCatch)) {

                                /* Multiple catch blocks or finally blocks wrap the same range of instructions */
                                if (coverSameRange(tryCatch, otherCatch)) {

                                    if (otherCatch.type == null) {
                                        for (TryCatchBlockNode finallyBlock : mn.tryCatchBlocks) {
                                            if (! tryCatch.equals(finallyBlock) && !otherCatch.equals(finallyBlock)) {
                                                if (finallyBlock.handler.equals(otherCatch.handler) && finallyBlock.type == null) {

                                                    /* We have identified a try catch finally block pattern.
                                                    The current overestimation of our instruction removal removes
                                                    the finally block instructions. We remedy that to only remove the
                                                    catch block range of instructions.
                                                     */

                                                    List<AbstractInsnNode> instructionsInCatchRange = getInstructionsInRange(finallyBlock.start, finallyBlock.handler, il);
                                                    blocksToRemove.put(otherCatch, instructionsInCatchRange);
                                                    blocksToRemove.put(finallyBlock, new ArrayList<>());
                                                    blocksToRemove.remove(tryCatch);
                                                    break;
                                                }
                                            }
                                        }
                                    } else {
                                        /* This try catch has multiple exception handling catch blocks.
                                        We have overestimated the instructions that need removing if further
                                        catch blocks exist after the ClassNotFoundException block.
                                        Remedy this overestimation */

                                        List<AbstractInsnNode> instructionsInRelevantCatch = null;
                                        if (il.indexOf(tryCatch.handler) < il.indexOf(otherCatch.handler)) {
                                            instructionsInRelevantCatch = getInstructionsInRange(tryCatch.handler.getPrevious(), otherCatch.handler.getPrevious(), il);
                                            blocksToRemove.put(tryCatch, instructionsInRelevantCatch);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for (TryCatchBlockNode toRemove : blocksToRemove.keySet()) {
                    for (AbstractInsnNode r : blocksToRemove.get(toRemove)) {
                        if (il.indexOf(r) != -1) {
                            il.remove(r);
                        }

                    }
                    mn.tryCatchBlocks.remove(toRemove);
                }
            }

        }
    }

    private List<Integer> getIndicesInRange(int start, int end, Map<Integer, Integer> indices) {
        List<Integer> indicesInRange = new ArrayList<>();
        for (Integer index : indices.keySet()) {
            if (index >= start  && index <= end) {
                indicesInRange.add(index);
            }
        }
        return indicesInRange;
    }

    /**
     * Returns whether all the Class.forName calls within a given range are transformed.
     * We don't want to remove exception handlers if calls to Class.forName still exist.
     * @param start start index
     * @param end end index
     * @param insn current instruction
     * @param tryCatchBlockNodes try catch block nodes corresponding to current method node
     * @param il instruction list for current method node
     * @return
     */
    private boolean allCallsTransformed(int start, int end, AbstractInsnNode insn, List<TryCatchBlockNode> tryCatchBlockNodes, InsnList il) {
        for (int i = start; i < end; i++) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode) insn;
                if (m.getOpcode() == Opcodes.INVOKESTATIC && m.name.equals("forName")
                        && m.owner.equals("java/lang/Class") && m.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {

                    /* Check if this call is covered by another handler.  If it is, we consider them "transformed" */
                    boolean isCovered = false;
                    for (TryCatchBlockNode node : tryCatchBlockNodes) {
                        int startNode = il.indexOf(node.start);
                        int endNode = il.indexOf(node.end);
                        int index = il.indexOf(m);
                        if (index >= startNode && index <= endNode) {
                            isCovered = true;
                        }
                    }
                    if (! isCovered) {
                        return false;
                    }
                }
            }
            insn = insn.getNext();
        }
        return true;
    }

    private boolean coverSameRange(TryCatchBlockNode node1, TryCatchBlockNode node2) {
        return node1.start.equals(node2.start) && node1.end.equals(node2.end);
    }

    /**
     * Returns whether we have the following type of handling "catch (Exception1 | Exception2 e)"
     * @param node1 first try catch block node
     * @param node2 second try catch block node
     * @return
     */
    private boolean isJointException(TryCatchBlockNode node1, TryCatchBlockNode node2) {
        return coverSameRange(node1, node2) && node1.handler.equals(node2.handler);
    }

    /**
     * Populates a hash map that dictates the relationship between a try catch block node
     * and the instructions that need to be removed if exception handlers are no longer used.
     * @param tryCatchBlockNodes the try catch block nodes for the current method node
     * @param il the instruction list for the current method node
     * @return
     */
    private Map<TryCatchBlockNode, List<AbstractInsnNode>> populateCatchInstructionMap(
            List<TryCatchBlockNode> tryCatchBlockNodes, InsnList il) {
        Map<TryCatchBlockNode, List<AbstractInsnNode>> map = new HashMap<>();

        for (TryCatchBlockNode tryCatch : tryCatchBlockNodes) {
            List<AbstractInsnNode> instructions = new ArrayList<>();
            AbstractInsnNode insn = tryCatch.handler;
            int currIndex = il.indexOf(tryCatch.handler);
            AbstractInsnNode handlerPrevInsn = tryCatch.handler.getPrevious();
            if (handlerPrevInsn instanceof JumpInsnNode) {
                int jumpIndex = il.indexOf(((JumpInsnNode) handlerPrevInsn).label);
                instructions.add(insn.getPrevious()); // add jump instruction
                while (currIndex < jumpIndex - 1) {
                    insn = insn.getNext();
                    currIndex = il.indexOf(insn);
                    instructions.add(insn);
                }
                map.put(tryCatch, instructions);
            }
        }

        map = purgeJointExceptions(map);

        return map;
    }

    /**
     * Removes try catch block nodes that represent joint exceptions from the map
     * @param instructionMap the map with try catch block nodes and instructions that need to be removed
     * @return
     */
    private Map<TryCatchBlockNode, List<AbstractInsnNode>>  purgeJointExceptions(Map<TryCatchBlockNode, List<AbstractInsnNode>> instructionMap) {
        Map<TryCatchBlockNode, List<AbstractInsnNode>> newMap = new HashMap<>();
        for (TryCatchBlockNode block : instructionMap.keySet()) {
            boolean independent = true;
            for (TryCatchBlockNode other : instructionMap.keySet()) {
                if (! block.equals(other)) {
                    if (isJointException(block, other)) {
                        independent = false;
                        break;
                    }
                }
            }
            if (independent) newMap.put(block, instructionMap.get(block));
        }
        return newMap;
    }

    private List<AbstractInsnNode> getInstructionsInRange(AbstractInsnNode start, AbstractInsnNode end, InsnList il) {
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (int i = il.indexOf(start); i < il.indexOf(end); i++) {
            AbstractInsnNode insn = il.get(i);
            if (! (insn instanceof LineNumberNode) && ! (insn instanceof LabelNode)) {
                instructions.add(insn);
            }
        }
        return instructions;
    }

    private ResourcePoolEntry transform(ResourcePoolEntry resource, ResourcePool pool) {
        byte[] inBytes = resource.contentBytes();
        ClassReader cr = new ClassReader(inBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, SKIP_FRAMES);
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
            if (modified) removeUnreachableExceptionHandlers(mn, il);
        }

        if (modified) {
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
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

                    // TODO remove java.base check. In place right now to make debugging easier.
                    if (path.endsWith(".class") && !path.endsWith("/module-info.class") && ! resource.moduleName().equals("java.base")) {
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
