package example.airgap.routes;

import io.github.fiftieshousewife.airgap.agent.Hook;
import java.util.ArrayDeque;
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

/**
 * The JDK's public methods that can reach one of its last steps before a risky call (a native connect, send, lookup,
 * process start, library load or exit) without passing one of the agent's hooks.
 */
final class Routes {

    /** Each kind's last steps: the JDK's own native methods, by class and name, on any supported JDK. */
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
        steps.put("tcp", Arrays.asList("sun/nio/ch/Net.connect0", "java/net/PlainSocketImpl.socketConnect",
                "java/net/DualStackPlainSocketImpl.connect0", "sun/nio/ch/sctp/SctpNet.connect0"));
        steps.put("udp", Arrays.asList("sun/nio/ch/DatagramChannelImpl.send0",
                "java/net/PlainDatagramSocketImpl.send", "java/net/PlainDatagramSocketImpl.send0",
                "java/net/PlainDatagramSocketImpl.connect0",
                "java/net/DualStackPlainDatagramSocketImpl.socketSend",
                "java/net/DualStackPlainDatagramSocketImpl.socketConnect"));
        steps.put("host-lookup", Arrays.asList("java/net/Inet4AddressImpl.lookupAllHostAddr",
                "java/net/Inet6AddressImpl.lookupAllHostAddr", "java/net/Inet4AddressImpl.getHostByAddr",
                "java/net/Inet6AddressImpl.getHostByAddr", "java/net/Inet4AddressImpl.isReachable0",
                "java/net/Inet6AddressImpl.isReachable0"));
        steps.put("process", Arrays.asList("java/lang/ProcessImpl.forkAndExec", "java/lang/ProcessImpl.create",
                "java/lang/UNIXProcess.forkAndExec"));
        steps.put("native-library", Arrays.asList("java/lang/ClassLoader$NativeLibrary.load0",
                "java/lang/ClassLoader$NativeLibrary.load", "jdk/internal/loader/NativeLibraries.load",
                "jdk/internal/loader/RawNativeLibraries.load0"));
        steps.put("exit", Arrays.asList("java/lang/Shutdown.halt0"));
        return Collections.unmodifiableMap(steps);
    }

    /**
     * The methods the agent's refusing hooks rewrite, worked out from the hooks themselves, as the self-check does.
     * Hooks that only hand a test over to other threads guard nothing, so a route through them goes on.
     */
    private static Set<String> findHookedMethods() {
        final Set<String> methods = new HashSet<>();
        for (final Hook hook : Hook.values()) {
            if (!hook.refuses()) {
                continue;
            }
            final TypeDescription type;
            try {
                type = TypeDescription.ForLoadedType.of(Class.forName(hook.getTypeName(), false, null));
            } catch (ClassNotFoundException notOnThisJdk) {
                continue;
            }
            for (final MethodDescription method : type.getDeclaredMethods().filter(hook.getMethods())) {
                methods.add(type.getInternalName() + "." + method.getInternalName() + method.getDescriptor());
            }
        }
        return methods;
    }

    /** @return the last steps of this kind found on this JDK */
    List<String> lastStepsOf(final String kind) {
        final List<String> found = new java.util.ArrayList<>();
        for (final String step : LAST_STEPS.get(kind)) {
            final int dot = step.lastIndexOf('.');
            found.addAll(graph.methodsNamed(step.substring(0, dot), step.substring(dot + 1)));
        }
        return found;
    }

    /**
     * Walks back from a kind's last steps, never through a hooked method, noting each public JDK method on the way.
     * The walk stops at a public method unless it is abstract: then JDK code calling it may lead to other ones.
     *
     * @param kind the kind of call
     * @return each public method reached, with one route from it to a last step
     */
    Map<String, String> unhooked(final String kind) {
        final Map<String, String> routeTo = new HashMap<>();
        final Deque<String> queue = new ArrayDeque<>();
        for (final String step : lastStepsOf(kind)) {
            routeTo.put(step, step);
            queue.add(step);
        }
        final Map<String, String> found = new TreeMap<>();
        while (!queue.isEmpty()) {
            final String method = queue.remove();
            if (isGeneral(method)) {
                continue;
            }
            final boolean api = graph.isApi(method);
            if (api) {
                found.put(method, routeTo.get(method));
            }
            if (!api || graph.isAbstract(method)) {
                walkToCallers(method, routeTo, queue);
            }
        }
        return found;
    }

    private void walkToCallers(final String method, final Map<String, String> routeTo, final Deque<String> queue) {
        for (final String caller : graph.callersOf(method)) {
            if (!hookedMethods.contains(caller) && !routeTo.containsKey(caller)) {
                routeTo.put(caller, caller + " -> " + routeTo.get(method));
                queue.add(caller);
            }
        }
    }

    /**
     * Whether a method is one every kind of object has, or a general callback's, such as {@code Runnable.run}. A route
     * through one carries on from the code that made the callback instead (see {@link CallGraph}), since following
     * everything that calls {@code hashCode} or {@code run} would lead everywhere.
     */
    private static boolean isGeneral(final String method) {
        for (final String owner : GENERAL) {
            if (method.startsWith(owner + ".")) {
                return true;
            }
        }
        return method.startsWith("java/util/function/");
    }

    /** @return the hooked methods found on this JDK */
    Set<String> hooked() {
        return Collections.unmodifiableSet(hookedMethods);
    }
}
