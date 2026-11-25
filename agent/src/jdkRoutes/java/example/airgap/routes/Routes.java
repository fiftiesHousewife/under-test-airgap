package example.airgap.routes;

import io.github.fiftieshousewife.airgap.agent.Hook;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

// The JDK's public methods that can reach one of its last steps before a risky call (a native connect, send, lookup,
// process start, library load, exit, or file open, delete, rename, link or new folder) without passing one of the
// agent's hooks. LAST_STEPS holds each kind's last steps, the JDK's own native methods by class and name on any
// supported JDK, as last-steps.txt lists them.
final class Routes {

    static final Map<String, List<String>> LAST_STEPS = lastSteps();

    private static final List<String> GENERAL = Arrays.asList("java/lang/Object", "java/lang/Runnable",
            "java/util/concurrent/Callable", "java/security/PrivilegedAction",
            "java/security/PrivilegedExceptionAction", "java/lang/Iterable", "java/util/Iterator",
            "java/lang/Comparable", "java/lang/AutoCloseable", "java/io/Closeable", "java/lang/Thread");

    private final CallGraph graph;
    private final Set<String> hookedMethods;

    Routes(final CallGraph graph) {
        this.graph = graph;
        this.hookedMethods = findHookedMethods();
    }

    private static Map<String, List<String>> lastSteps() {
        final Map<String, List<String>> steps = new TreeMap<>();
        for (final String line : ResourceLines.read("last-steps.txt")) {
            final String[] kindAndStep = line.split("\\s+", 2);
            steps.computeIfAbsent(kindAndStep[0], kind -> new ArrayList<>()).add(kindAndStep[1]);
        }
        return Collections.unmodifiableMap(steps);
    }

    // The methods the agent's refusing hooks rewrite, worked out from the hooks themselves, as the self-check does.
    // Hooks that only hand a test over to other threads guard nothing, so a route through them goes on.
    private static Set<String> findHookedMethods() {
        final Set<String> methods = new HashSet<>();
        for (final Hook hook : Hook.values()) {
            if (hook.refuses()) {
                methods.addAll(methodsOf(hook));
            }
        }
        return methods;
    }

    // The methods one hook rewrites on this JDK; none if its class isn't on it
    private static List<String> methodsOf(final Hook hook) {
        final TypeDescription type;
        try {
            type = TypeDescription.ForLoadedType.of(Class.forName(hook.getTypeName(), false, null));
        } catch (ClassNotFoundException notOnThisJdk) {
            return Collections.emptyList();
        }
        final List<String> methods = new ArrayList<>();
        for (final MethodDescription method : type.getDeclaredMethods().filter(hook.getMethods())) {
            methods.add(type.getInternalName() + "." + method.getInternalName() + method.getDescriptor());
        }
        return methods;
    }

    // Returns the last steps of this kind found on this JDK
    List<String> lastStepsOf(final String kind) {
        final List<String> found = new ArrayList<>();
        for (final String step : LAST_STEPS.get(kind)) {
            final int dot = step.lastIndexOf('.');
            found.addAll(graph.methodsNamed(step.substring(0, dot), step.substring(dot + 1)));
        }
        return found;
    }

    // Returns each public method that reaches a last step of the kind, with one route from it to that step
    Map<String, String> unhooked(final String kind) {
        return new Walk(lastStepsOf(kind)).run();
    }

    // Returns the hooked methods found on this JDK
    Set<String> hooked() {
        return Collections.unmodifiableSet(hookedMethods);
    }

    // Whether a method is one every kind of object has, or a general callback's, such as Runnable.run. A route
    // through one carries on from the code that made the callback instead (see CallGraph), since following
    // everything that calls hashCode or run would lead everywhere.
    private static boolean isGeneral(final String method) {
        for (final String owner : GENERAL) {
            if (method.startsWith(owner + ".")) {
                return true;
            }
        }
        return method.startsWith("java/util/function/");
    }

    // A walk back from a kind's last steps, never through a hooked method, noting each public JDK method on the way.
    // It stops at a public method unless it is abstract: then JDK code calling it may lead to other ones.
    private final class Walk {
        private final Map<String, String> routeTo = new HashMap<>();
        private final Deque<String> queue = new ArrayDeque<>();
        private final Map<String, String> found = new TreeMap<>();

        Walk(final List<String> lastSteps) {
            for (final String step : lastSteps) {
                routeTo.put(step, step);
                queue.add(step);
            }
        }

        Map<String, String> run() {
            while (!queue.isEmpty()) {
                visit(queue.remove());
            }
            return found;
        }

        private void visit(final String method) {
            if (isGeneral(method)) {
                return;
            }
            final boolean api = graph.isApi(method);
            if (api) {
                found.put(method, routeTo.get(method));
            }
            if (!api || graph.isAbstract(method)) {
                walkToCallers(method);
            }
        }

        private void walkToCallers(final String method) {
            for (final String caller : graph.callersOf(method)) {
                if (!hookedMethods.contains(caller) && !routeTo.containsKey(caller)) {
                    routeTo.put(caller, caller + " -> " + routeTo.get(method));
                    queue.add(caller);
                }
            }
        }
    }
}
