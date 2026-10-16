package io.github.apertechprojects.queryforge.internal.expression;

import io.github.apertechprojects.queryforge.exception.QueryForgeUnsupportedExpressionException;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instructions of one method, read with the ASM copy bundled in spring-core.
 */
final class MethodCode {

    static final class Insn {
        final int opcode;
        final int line;
        int operand;
        int increment;
        String owner;
        String name;
        String descriptor;
        Object constant;
        Handle bootstrap;
        Object[] bootstrapArguments;
        Label label;
        int target = -1;

        Insn(int opcode, int line) {
            this.opcode = opcode;
            this.line = line;
        }
    }

    final String className;
    final String methodName;
    final List<Insn> instructions;
    final int maxLocals;

    private MethodCode(String className, String methodName, List<Insn> instructions, int maxLocals) {
        this.className = className;
        this.methodName = methodName;
        this.instructions = instructions;
        this.maxLocals = maxLocals;
    }

    static MethodCode read(ClassLoader classLoader, String internalClassName, String methodName, String descriptor) {
        String resource = internalClassName + ".class";
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new QueryForgeUnsupportedExpressionException("Bytecode of " + internalClassName.replace('/', '.') + " is not available");
            }
            Recorder recorder = new Recorder(methodName, descriptor);
            new ClassReader(input).accept(recorder, ClassReader.SKIP_FRAMES);
            if (recorder.method == null) {
                throw new QueryForgeUnsupportedExpressionException("Method " + methodName + descriptor + " not found in " + internalClassName.replace('/', '.'));
            }
            return new MethodCode(internalClassName.replace('/', '.'), methodName, recorder.method.resolve(), recorder.method.maxLocals);
        } catch (IOException e) {
            throw new QueryForgeUnsupportedExpressionException("Bytecode of " + internalClassName.replace('/', '.') + " could not be read", e);
        }
    }

    private static final class Recorder extends ClassVisitor {
        private final String methodName;
        private final String descriptor;
        private InstructionRecorder method;

        Recorder(String methodName, String descriptor) {
            super(SpringAsmInfo.ASM_VERSION);
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals(methodName) && descriptor.equals(this.descriptor)) {
                method = new InstructionRecorder();
                return method;
            }
            return null;
        }
    }

    private static final class InstructionRecorder extends MethodVisitor {
        private final List<Insn> instructions = new ArrayList<>();
        private final Map<Label, Integer> labels = new IdentityHashMap<>();
        private int line = -1;
        private int maxLocals;

        InstructionRecorder() {
            super(SpringAsmInfo.ASM_VERSION);
        }

        private Insn add(int opcode) {
            Insn insn = new Insn(opcode, line);
            instructions.add(insn);
            return insn;
        }

        @Override
        public void visitLabel(Label label) {
            labels.put(label, instructions.size());
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            this.line = line;
        }

        @Override
        public void visitInsn(int opcode) {
            add(opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            add(opcode).operand = operand;
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            add(opcode).operand = varIndex;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            add(opcode).owner = type;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            Insn insn = add(opcode);
            insn.owner = owner;
            insn.name = name;
            insn.descriptor = descriptor;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            Insn insn = add(opcode);
            insn.owner = owner;
            insn.name = name;
            insn.descriptor = descriptor;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            Insn insn = add(Opcodes.INVOKEDYNAMIC);
            insn.name = name;
            insn.descriptor = descriptor;
            insn.bootstrap = bootstrapMethodHandle;
            insn.bootstrapArguments = bootstrapMethodArguments;
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            add(opcode).label = label;
        }

        @Override
        public void visitLdcInsn(Object value) {
            add(Opcodes.LDC).constant = value;
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            Insn insn = add(Opcodes.IINC);
            insn.operand = varIndex;
            insn.increment = increment;
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            add(Opcodes.TABLESWITCH);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            add(Opcodes.LOOKUPSWITCH);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            add(Opcodes.MULTIANEWARRAY).descriptor = descriptor;
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            this.maxLocals = maxLocals;
        }

        List<Insn> resolve() {
            for (Insn insn : instructions) {
                if (insn.label != null) {
                    insn.target = labels.get(insn.label);
                }
            }
            return List.copyOf(instructions);
        }
    }
}
