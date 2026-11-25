package example.airgap.routes;

import java.util.Collections;
import java.util.List;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.ModuleVisitor;
import net.bytebuddy.jar.asm.Opcodes;

// ASM's visitors, which read one class file: its methods, the calls each makes, and, for a module, what it exports.
// They override ASM's own methods, whose signatures take more parameters than the code-quality rules allow, so this
// file alone is left out of those checks (underTest.codeStyleExcludes in agent/build.gradle.kts).
final class ClassFileVisitors {

    private ClassFileVisitors() {
    }

    // Reads into the table, and adds each call as {caller, owner, name and descriptor} to the calls
    static ClassVisitor reader(final TypeTable table, final List<String[]> calls) {
        return new Reader(table, calls);
    }

    private static final class Reader extends ClassVisitor {
        private final TypeTable table;
        private final List<String[]> calls;
        private TypeTable.Type type;

        Reader(final TypeTable table, final List<String[]> calls) {
            super(Opcodes.ASM9);
            this.table = table;
            this.calls = calls;
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature,
                final String superName, final String[] interfaces) {
            type = new TypeTable.Type(name, superName, (access & Opcodes.ACC_PUBLIC) != 0);
            if (interfaces != null) {
                Collections.addAll(type.interfaces, interfaces);
            }
            table.add(type);
        }

        @Override
        public ModuleVisitor visitModule(final String name, final int access, final String version) {
            return new ModuleVisitor(Opcodes.ASM9) {
                @Override
                public void visitExport(final String packaze, final int exportAccess, final String... modules) {
                    if (modules == null) {
                        table.export(packaze);
                    }
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                final String signature, final String[] exceptions) {
            if (type == null) {
                return null;
            }
            type.methods.put(name + descriptor, access);
            return new Calls(type.name + "." + name + descriptor, calls);
        }
    }

    // Records each call one method makes, including the methods its lambdas and method references name.
    private static final class Calls extends MethodVisitor {
        private final String caller;
        private final List<String[]> calls;

        Calls(final String caller, final List<String[]> calls) {
            super(Opcodes.ASM9);
            this.caller = caller;
            this.calls = calls;
        }

        @Override
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor,
                final boolean isInterface) {
            calls.add(new String[] {caller, owner, name + descriptor});
        }

        @Override
        public void visitInvokeDynamicInsn(final String name, final String descriptor, final Handle bootstrap,
                final Object... arguments) {
            for (final Object argument : arguments) {
                handle(argument);
            }
        }

        @Override
        public void visitLdcInsn(final Object value) {
            handle(value);
        }

        private void handle(final Object value) {
            if (value instanceof Handle) {
                final Handle handle = (Handle) value;
                calls.add(new String[] {caller, handle.getOwner(), handle.getName() + handle.getDesc()});
            }
        }
    }
}
