package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * The methods of hooks whose signature differs between JDKs, and the classes some JDKs lack, worked out on the
 * running one.
 */
public final class HookMethods {

    public static final String SECURE_UNIX_DIRECTORY_STREAM = "sun.nio.fs.UnixSecureDirectoryStream";

    public static final Set<String> HOOKS_ONLY_SOME_JDKS_HAVE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "TCP_SCTP_CONNECT", "TCP_SCTP_SEND", "NATIVE_LIBRARY_FOREIGN_BY_NAME", "NATIVE_LIBRARY_FOREIGN_BY_PATH",
            "TCP_HTTP_CLIENT_TASKS", "TCP_HTTP_CLIENT_SEND", "FILE_WRITE_SECURE_FOLDER", "FILE_WRITE_SECURE_DELETE",
            "FILE_WRITE_SECURE_MOVE", "FILE_WRITE_SECURE_CHANNEL", "TCP_HTTP_CLIENT_PROXY")));
    public static final Set<String> HOOKS_THAT_GUARD_NO_ROUTE = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("TCP_HTTP_CLIENT_TASKS", "TCP_HTTP_CLIENT_SEND", "FILE_WRITE_SECURE_FOLDER",
                    "TCP_URL_CONNECTION_PROXY", "TCP_HTTP_CLIENT_PROXY")));

    private HookMethods() {
    }

    /** @return the private {@code getAllByName0} with the most parameters: the one the others reach, above the cache */
    public static ElementMatcher<MethodDescription> innermostLookup() {
        Method innermost = null;
        for (final Method method : InetAddress.class.getDeclaredMethods()) {
            if (isPrivateLookup(method)) {
                innermost = theOneTakingMore(innermost, method);
            }
        }
        return innermost == null ? none()
                : named("getAllByName0").and(takesArguments(innermost.getParameterTypes()));
    }

    private static boolean isPrivateLookup(final Method method) {
        return "getAllByName0".equals(method.getName()) && Modifier.isPrivate(method.getModifiers());
    }

    private static Method theOneTakingMore(final Method soFar, final Method method) {
        return soFar == null || method.getParameterCount() > soFar.getParameterCount() ? method : soFar;
    }

    /**
     * @return the class of the default file system that declares its open, delete, copy, move and link methods:
     *         Windows' own, or the one Linux, macOS and the other Unix systems share
     */
    public static String fileSystemProvider() {
        final String unix = "sun.nio.fs.UnixFileSystemProvider";
        return load(unix) == null ? "sun.nio.fs.WindowsFileSystemProvider" : unix;
    }

    /**
     * @param type the class
     * @param method the start of a method's name, or empty for any method
     * @return whether the class is on this JDK and declares such a method
     */
    public static boolean declares(final String type, final String method) {
        final Class<?> loaded = load(type);
        if (loaded == null) {
            return false;
        }
        for (final Method declared : loaded.getDeclaredMethods()) {
            if (declared.getName().startsWith(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads a hooked class without initialising it, through the system class loader, which sees the JDK's classes
     * from every module.
     *
     * @param type the class
     * @return the class, or {@code null} if this JDK has none
     */
    public static Class<?> load(final String type) {
        try {
            return Class.forName(type, false, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException | LinkageError notOnThisJdk) {
            return null;
        }
    }

    /** @return {@code ProcessBuilder}'s private {@code start(Redirect[])} from JDK 9, or {@code start()} on JDK 8 */
    public static ElementMatcher<MethodDescription> processStart() {
        try {
            ProcessBuilder.class.getDeclaredMethod("start", ProcessBuilder.Redirect[].class);
            return named("start").and(takesArguments(ProcessBuilder.Redirect[].class));
        } catch (NoSuchMethodException jdk8) {
            return named("start").and(takesArguments(0));
        }
    }
}
