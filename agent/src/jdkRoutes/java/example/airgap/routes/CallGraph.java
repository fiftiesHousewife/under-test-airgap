package example.airgap.routes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.ModuleVisitor;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * Who can call whom inside the JDK. A method is named {@code owner.name(descriptor)}, with the owner in internal form
 * ({@code java/net/Socket}). Besides plain calls, a call to a method also counts as a call to every method overriding
 * it, and a lambda or method reference counts as a call from the method that makes it.
 */
final class CallGraph {

    /** What one class declares. */
    static final class Type {
        final String name;
        final String superName;
        final List<String> interfaces = new ArrayList<>();
        final boolean isPublic;
        final Map<String, Integer> methods = new HashMap<>();

        Type(final String name, final String superName, final boolean isPublic) {
            this.name = name;
            this.superName = superName;
            this.isPublic = isPublic;
        }
    }

    /** JDK 8 has no modules: its public API is the Java SE packages and the few JDK ones it supports. */
    private static final List<String> JDK_8_API = Collections.unmodifiableList(java.util.Arrays.asList("java/",
            "javax/", "org/ietf/", "org/omg/", "com/sun/net/httpserver", "com/sun/nio/sctp", "jdk/net"));

    private final Map<String, Type> types = new HashMap<>();
    private final Map<String, Set<String>> callers = new HashMap<>();
    private final Set<String> exportedPackages = new HashSet<>();
    private final List<String[]> rawCalls = new ArrayList<>();
    private final Map<String, List<String>> ancestorsOf = new HashMap<>();

    /** @param classFile one class file */
    void add(final byte[] classFile) {
        new ClassReader(classFile).accept(new Reader(), ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    }

    /** Joins each call to the method it reaches, and each method to those overriding it. Call once, after adding. */
    void link() {
        for (final String[] call : rawCalls) {
            final String target = resolve(call[1], call[2]);
            callers.computeIfAbsent(target == null ? call[1] + "." + call[2] : target, key -> new HashSet<>())
                    .add(call[0]);
        }
        rawCalls.clear();
        linkCallbacks();
        for (final Type type : types.values()) {
            for (final Map.Entry<String, Integer> method : type.methods.entrySet()) {
                if ((method.getValue() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
                    linkOverridden(type, method.getKey());
                }
            }
        }
    }

    /**
     * An anonymous class is a callback: whoever makes one is taken to call its methods, since the JDK hands it to
     * code that calls it through a general interface such as {@code Runnable} or {@code PrivilegedAction}.
     */
    private void linkCallbacks() {
        for (final Type type : types.values()) {
            if (!isAnonymous(type.name)) {
                continue;
            }
            final Set<String> makers = new HashSet<>();
            for (final String method : type.methods.keySet()) {
                if (method.startsWith("<init>")) {
                    makers.addAll(callersOf(type.name + "." + method));
                }
            }
            for (final String method : type.methods.keySet()) {
                callers.computeIfAbsent(type.name + "." + method, key -> new HashSet<>()).addAll(makers);
            }
        }
    }

    private static boolean isAnonymous(final String name) {
        final int dollar = name.lastIndexOf('$');
        if (dollar < 0 || dollar == name.length() - 1) {
            return false;
        }
        for (int i = dollar + 1; i < name.length(); i++) {
            if (name.charAt(i) < '0' || name.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private void linkOverridden(final Type type, final String method) {
        final String overriding = type.name + "." + method;
        for (final String ancestor : ancestors(type.name)) {
            final Type declaring = types.get(ancestor);
            if (declaring != null && declaring.methods.containsKey(method)) {
                callers.computeIfAbsent(overriding, key -> new HashSet<>()).add(ancestor + "." + method);
            }
        }
    }

    /** @return the method a call on {@code owner} reaches, found up its supertypes, or {@code null} */
    private String resolve(final String owner, final String method) {
        if (declares(owner, method)) {
            return owner + "." + method;
        }
        for (final String ancestor : ancestors(owner)) {
            if (declares(ancestor, method)) {
                return ancestor + "." + method;
            }
        }
        return null;
    }

    private boolean declares(final String owner, final String method) {
        final Type type = types.get(owner);
        return type != null && type.methods.containsKey(method);
    }

    private List<String> ancestors(final String name) {
        return ancestorsOf.computeIfAbsent(name, this::findAncestors);
    }

    private List<String> findAncestors(final String name) {
        final List<String> found = new ArrayList<>();
        final List<String> queue = new ArrayList<>(Collections.singletonList(name));
        while (!queue.isEmpty()) {
            final Type type = types.get(queue.remove(0));
            if (type == null) {
                continue;
            }
            final List<String> parents = new ArrayList<>(type.interfaces);
            if (type.superName != null) {
                parents.add(type.superName);
            }
            for (final String parent : parents) {
                if (!found.contains(parent)) {
                    found.add(parent);
                    queue.add(parent);
                }
            }
        }
        return found;
    }

    /** @return the methods that call this one */
    Set<String> callersOf(final String method) {
        return callers.getOrDefault(method, Collections.<String>emptySet());
    }

    /** @return every method whose owner is this class and whose name is this, whatever its descriptor */
    List<String> methodsNamed(final String owner, final String name) {
        final List<String> found = new ArrayList<>();
        final Type type = types.get(owner);
        if (type != null) {
            for (final String method : type.methods.keySet()) {
                if (method.startsWith(name + "(")) {
                    found.add(owner + "." + method);
                }
            }
        }
        return found;
    }

    /** @return whether a method is abstract: an interface's or an abstract class's, run by whatever implements it */
    boolean isAbstract(final String method) {
        final Integer access = accessOf(method);
        return access != null && (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    private Integer accessOf(final String method) {
        final int dot = method.indexOf('.');
        final Type type = types.get(method.substring(0, dot));
        return type == null ? null : type.methods.get(method.substring(dot + 1));
    }

    /**
     * Whether a method is part of the JDK's public API: public or protected, in a public class, in a package its
     * module exports to everyone (on JDK 8, a package of the Java SE platform).
     */
    boolean isApi(final String method) {
        final Type type = types.get(method.substring(0, method.indexOf('.')));
        final Integer access = accessOf(method);
        if (type == null || access == null || !type.isPublic) {
            return false;
        }
        final String packageName = type.name.substring(0, Math.max(type.name.lastIndexOf('/'), 0));
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && isExported(packageName);
    }

    private boolean isExported(final String packageName) {
        if (!exportedPackages.isEmpty()) {
            return exportedPackages.contains(packageName);
        }
        for (final String prefix : JDK_8_API) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Reads one class: its methods, the calls each makes, and, for a module, what it exports. */
    private final class Reader extends ClassVisitor {
        private Type type;

        Reader() {
            super(Opcodes.ASM9);
            type = null;
        }

        @Override
        @SuppressWarnings("PMD.ExcessiveParameterList") // ASM's signature
        public void visit(final int version, final int access, final String name, final String signature,
                final String superName, final String[] interfaces) {
            type = new Type(name, superName, (access & Opcodes.ACC_PUBLIC) != 0);
            if (interfaces != null) {
                Collections.addAll(type.interfaces, interfaces);
            }
            types.put(name, type);
        }

        @Override
        public ModuleVisitor visitModule(final String name, final int access, final String version) {
            return new ModuleVisitor(Opcodes.ASM9) {
                @Override
                public void visitExport(final String packaze, final int exportAccess, final String... modules) {
                    if (modules == null) {
                        exportedPackages.add(packaze);
                    }
                }
            };
        }

        @Override
        @SuppressWarnings("PMD.ExcessiveParameterList") // ASM's signature
        public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                final String signature, final String[] exceptions) {
            if (type == null) {
                return null;
            }
            type.methods.put(name + descriptor, access);
            return new Calls(type.name + "." + name + descriptor);
        }
    }

    /** Records each call one method makes, including the methods its lambdas and method references name. */
    private final class Calls extends MethodVisitor {
        private final String caller;

        Calls(final String caller) {
            super(Opcodes.ASM9);
            this.caller = caller;
        }

        @Override
        @SuppressWarnings("PMD.ExcessiveParameterList") // ASM's signature
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor,
                final boolean isInterface) {
            rawCalls.add(new String[] {caller, owner, name + descriptor});
        }

        @Override
        @SuppressWarnings("PMD.ExcessiveParameterList") // ASM's signature
        public void visitInvokeDynamicInsn(final String name, final String descriptor, final Handle bootstrap,
                final Object... arguments) {
            for (final Object argument : arguments) {
                if (argument instanceof Handle) {
                    final Handle handle = (Handle) argument;
                    rawCalls.add(new String[] {caller, handle.getOwner(), handle.getName() + handle.getDesc()});
                }
            }
        }

        @Override
        public void visitLdcInsn(final Object value) {
            if (value instanceof Handle) {
                final Handle handle = (Handle) value;
                rawCalls.add(new String[] {caller, handle.getOwner(), handle.getName() + handle.getDesc()});
            }
        }
    }
}
