package example.airgap.routes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.Opcodes;

// Who can call whom inside the JDK. A method is named owner.name(descriptor), with the owner in internal form
// (java/net/Socket). Besides plain calls, a call to a method also counts as a call to every method overriding
// it, and a lambda or method reference counts as a call from the method that makes it.
final class CallGraph {

    private static final Pattern ANONYMOUS = Pattern.compile(".*\\$[0-9]+");

    private final TypeTable types = new TypeTable();
    private final Map<String, Set<String>> callers = new HashMap<>();
    private final List<String[]> rawCalls = new ArrayList<>();

    // classFile: one class file
    void add(final byte[] classFile) {
        new ClassReader(classFile).accept(ClassFileVisitors.reader(types, rawCalls),
                ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    }

    // Joins each call to the method it reaches, and each method to those overriding it. Call once, after adding.
    void link() {
        linkCalls();
        linkCallbacks();
        for (final TypeTable.Type type : types.all()) {
            linkOverrides(type);
        }
    }

    private void linkCalls() {
        for (final String[] call : rawCalls) {
            final String target = types.resolve(call[1], call[2]);
            addCaller(target == null ? call[1] + "." + call[2] : target, call[0]);
        }
        rawCalls.clear();
    }

    private void addCaller(final String method, final String caller) {
        callers.computeIfAbsent(method, key -> new HashSet<>()).add(caller);
    }

    // An anonymous class is a callback: whoever makes one is taken to call its methods, since the JDK hands it to
    // code that calls it through a general interface such as Runnable or PrivilegedAction.
    private void linkCallbacks() {
        for (final TypeTable.Type type : types.all()) {
            if (ANONYMOUS.matcher(type.name).matches()) {
                final Set<String> makers = makersOf(type);
                for (final String method : type.methods.keySet()) {
                    callers.computeIfAbsent(type.name + "." + method, key -> new HashSet<>()).addAll(makers);
                }
            }
        }
    }

    // The methods that make one of the class's objects: those that call its constructors
    private Set<String> makersOf(final TypeTable.Type type) {
        final Set<String> makers = new HashSet<>();
        for (final String method : type.methods.keySet()) {
            if (method.startsWith("<init>")) {
                makers.addAll(callersOf(type.name + "." + method));
            }
        }
        return makers;
    }

    // Each method a class's instances can override counts as called wherever an ancestor's method of that name is
    private void linkOverrides(final TypeTable.Type type) {
        for (final Map.Entry<String, Integer> method : type.methods.entrySet()) {
            if ((method.getValue() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) {
                linkOverridden(type, method.getKey());
            }
        }
    }

    private void linkOverridden(final TypeTable.Type type, final String method) {
        final String overriding = type.name + "." + method;
        for (final String ancestor : types.ancestors(type.name)) {
            if (types.declares(ancestor, method)) {
                addCaller(overriding, ancestor + "." + method);
            }
        }
    }

    // Returns the methods that call this one
    Set<String> callersOf(final String method) {
        return callers.getOrDefault(method, Collections.<String>emptySet());
    }

    // Returns every method whose owner is this class and whose name is this, whatever its descriptor
    List<String> methodsNamed(final String owner, final String name) {
        return types.methodsNamed(owner, name);
    }

    // Returns whether a method is abstract: an interface's or an abstract class's, run by whatever implements it
    boolean isAbstract(final String method) {
        return types.isAbstract(method);
    }

    // Whether a method is part of the JDK's public API (see TypeTable)
    boolean isApi(final String method) {
        return types.isApi(method);
    }
}
