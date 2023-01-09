package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.SourceInterpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.SourceValue;

import java.util.*;

/**
 * A SourceInterpreter interprets a bytecode instruction
 * that moves a value to the stack from local variables.
 *
 * The Frame<SourceValue> allows us to identify the instructions
 * which pushed the current values to the operand stack. However,
 * note in the case of an expression like 'a+b', the source value
 * would be the instruction 'iadd' and it would require analyzing
 * the 'iadd' instruction's frame to get its inputs.
 *
 * The ParameterSourceInterpreter avoids doing this work
 * per instruction by storing all direct and indirect input
 * sources recursively in a map.
 *
 * Source: https://stackoverflow.com/questions/60969392/java-asm-bytecode-find-all-instructions-belonging-to-a-specific-method-call
 */
public class ParameterSourceInterpreter extends SourceInterpreter {

    Map<AbstractInsnNode, List<SourceValue>> sources;

    public ParameterSourceInterpreter(int version, Map<AbstractInsnNode, List<SourceValue>> sources) {
        super(version);
        this.sources = sources;
    }

    @Override
    public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
        sources.computeIfAbsent(insn, x -> new ArrayList<>()).add(value);
        return super.unaryOperation(insn, value);
    }

    @Override
    public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2) {
        addAll(insn, Arrays.asList(v1, v2));
        return super.binaryOperation(insn, v1, v2);
    }

    @Override
    public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue v1, SourceValue v2, SourceValue v3) {
        addAll(insn, Arrays.asList(v1, v2, v3));
        return super.ternaryOperation(insn, v1, v2, v3);
    }

    @Override
    public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
        addAll(insn, values);
        return super.naryOperation(insn, values);
    }

    private void addAll(AbstractInsnNode insn, List<? extends SourceValue> values) {
        sources.computeIfAbsent(insn, x -> new ArrayList<>()).addAll(values);
    }
}
