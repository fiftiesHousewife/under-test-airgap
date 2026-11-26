package io.github.fiftieshousewife.airgap.agent.spring;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Spring environments the application has made, read by reflection through Spring's public API, since the agent
 * has no Spring: their active profiles, and the property whose value holds an address. An environment nobody holds
 * any more is dropped. Read only while the agent decides a call, so nothing read here is itself checked.
 */
public final class SpringEnvironments {

    private static final Map<Object, Long> MADE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<int[]> STARTING_UP = ThreadLocal.withInitial(() -> new int[1]);
    private static long made;
    private static volatile WeakReference<Object> refreshedLast = new WeakReference<>(null);

    private SpringEnvironments() {
    }

    /** @param environment a Spring {@code Environment} just made */
    public static void add(final Object environment) {
        synchronized (MADE) {
            made++;
            MADE.put(environment, made);
        }
    }

    /** @return the environments still held, made last first */
    public static List<Object> newestFirst() {
        final List<Map.Entry<Object, Long>> held;
        synchronized (MADE) {
            held = new ArrayList<>(MADE.entrySet());
        }
        held.sort((one, other) -> Long.compare(other.getValue(), one.getValue()));
        final List<Object> environments = new ArrayList<>();
        for (final Map.Entry<Object, Long> environment : held) {
            environments.add(environment.getKey());
        }
        return environments;
    }

    /** @param environment the environment of the context refreshed last, whose profiles a call is reported under */
    public static void refreshed(final Object environment) {
        refreshedLast = new WeakReference<>(environment);
    }

    /** @return the environment of the context refreshed last, or {@code null} */
    public static Object lastRefreshed() {
        return refreshedLast.get();
    }

    /**
     * @param environment a Spring {@code Environment}
     * @return its active profiles, or its default ones if none is active, as Spring itself uses them
     */
    public static List<String> activeProfiles(final Object environment) {
        final String[] active = (String[]) call(environment, "org.springframework.core.env.Environment",
                "getActiveProfiles");
        final String[] profiles = active == null || active.length > 0 ? active : (String[]) call(environment,
                "org.springframework.core.env.Environment", "getDefaultProfiles");
        return profiles == null ? Collections.emptyList() : new ArrayList<>(Arrays.asList(profiles));
    }

    /**
     * @param context a Spring {@code ApplicationContext}
     * @return its environment, or {@code null} if it isn't one
     */
    public static Object of(final Object context) {
        return call(context, "org.springframework.core.env.EnvironmentCapable", "getEnvironment");
    }

    /** Spring is starting an application or context on this thread, inside any already starting there. */
    public static void startUpBegins() {
        STARTING_UP.get()[0]++;
    }

    /** Spring has finished starting it, or failed to. */
    public static void startUpEnds() {
        STARTING_UP.get()[0] = Math.max(0, STARTING_UP.get()[0] - 1);
    }

    /** @return true while Spring is starting an application or context on this thread */
    public static boolean isStartingUp() {
        return STARTING_UP.get()[0] > 0;
    }

    // A public method of one of Spring's types, by name, called on the application's object: null if it isn't one.
    static Object call(final Object target, final String type, final String method) {
        return invoke(target, methodOf(target, type, method));
    }

    // The public method of one of Spring's types with this name and one parameter, or null if there's none.
    static Method methodOf(final Object target, final String type, final String name) {
        try {
            final Class<?> api = Class.forName(type, false, target.getClass().getClassLoader());
            for (final Method method : api.getMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            return null;
        } catch (ClassNotFoundException | LinkageError notSpring) {
            return null;
        }
    }

    static Object invoke(final Object target, final Method method, final Object... arguments) {
        try {
            return method == null ? null : method.invoke(target, arguments);
        } catch (ReflectiveOperationException | IllegalArgumentException notThatType) {
            return null;
        }
    }

}
