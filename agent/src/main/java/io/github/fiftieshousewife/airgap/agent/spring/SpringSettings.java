package io.github.fiftieshousewife.airgap.agent.spring;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * Which property of a Spring environment holds an address, and, where Spring Boot knows it, where that property was
 * read: {@code class path resource [application.properties] - 3:21}. The property sources are searched in Spring's
 * own order, so the one found is the one Spring uses.
 */
public final class SpringSettings {

    private static final String ORIGIN_LOOKUP = "org.springframework.boot.origin.OriginLookup";

    private SpringSettings() {
    }

    /**
     * @param environment a Spring {@code ConfigurableEnvironment}
     * @param address the host, or other text, a call tried to reach
     * @return the property's name and where it was read (or {@code null} if Spring Boot doesn't know), or an
     *     empty array if no property holds it
     */
    public static String[] holding(final Object environment, final String address) {
        final Object sources = SpringEnvironments.call(environment,
                "org.springframework.core.env.ConfigurableEnvironment", "getPropertySources");
        if (!(sources instanceof Iterable) || address.isEmpty()) {
            return new String[0];
        }
        for (final Object source : (Iterable<?>) sources) {
            final String name = propertyHolding(source, address);
            if (name != null) {
                return new String[] {name, originOf(source, name)};
            }
        }
        return new String[0];
    }

    private static String propertyHolding(final Object source, final String address) {
        final Object names = SpringEnvironments.call(source, "org.springframework.core.env.EnumerablePropertySource",
                "getPropertyNames");
        if (!(names instanceof String[])) {
            return null;
        }
        final Method getProperty = SpringEnvironments.methodOf(source, "org.springframework.core.env.PropertySource",
                "getProperty");
        for (final String name : (String[]) names) {
            final Object value = SpringEnvironments.invoke(source, getProperty, name);
            if (value != null && contains(String.valueOf(value), address)) {
                return name;
            }
        }
        return null;
    }

    // Host names compare without regard to case.
    private static boolean contains(final String value, final String address) {
        return Pattern.compile(Pattern.quote(address), Pattern.CASE_INSENSITIVE).matcher(value).find();
    }

    private static String originOf(final Object source, final String name) {
        try {
            final Method lookup = Class.forName(ORIGIN_LOOKUP, false, source.getClass().getClassLoader())
                    .getMethod("getOrigin", Object.class, Object.class);
            final Object origin = lookup.invoke(null, source, name);
            return origin == null ? null : origin.toString();
        } catch (ReflectiveOperationException | LinkageError notSpringBoot) {
            return null;
        }
    }
}
