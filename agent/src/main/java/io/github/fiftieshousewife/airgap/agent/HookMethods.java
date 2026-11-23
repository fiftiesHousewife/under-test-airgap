package io.github.fiftieshousewife.airgap.agent;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** The methods of hooks whose signature differs between JDKs, worked out on the running one. */
final class HookMethods {

    private HookMethods() {
    }

    /** The private {@code getAllByName0} with the most parameters: the one the others reach, which reads the cache. */
    static ElementMatcher<MethodDescription> innermostLookup() {
        Method innermost = null;
        for (final Method method : InetAddress.class.getDeclaredMethods()) {
            if ("getAllByName0".equals(method.getName()) && Modifier.isPrivate(method.getModifiers())
                    && (innermost == null || method.getParameterCount() > innermost.getParameterCount())) {
                innermost = method;
            }
        }
        return innermost == null ? none()
                : named("getAllByName0").and(takesArguments(innermost.getParameterTypes()));
    }

    static ElementMatcher<MethodDescription> processStart() {
        try {
            ProcessBuilder.class.getDeclaredMethod("start", ProcessBuilder.Redirect[].class);
            return named("start").and(takesArguments(ProcessBuilder.Redirect[].class));
        } catch (NoSuchMethodException jdk8) {
            return named("start").and(takesArguments(0));
        }
    }
}
