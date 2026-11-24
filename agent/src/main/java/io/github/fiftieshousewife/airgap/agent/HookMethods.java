package io.github.fiftieshousewife.airgap.agent;

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

/** The methods of hooks whose signature differs between JDKs, worked out on the running one. */
final class HookMethods {

    /** The JDK's secure directory stream, on Unix. */
    static final String SECURE_STREAM = "sun.nio.fs.UnixSecureDirectoryStream";

    /** The hooks whose class only some JDKs, or only some systems, have. */
    static final Set<String> SOMETIMES_THERE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "TCP_SCTP_CONNECT", "TCP_SCTP_SEND", "NATIVE_LIBRARY_FOREIGN_BY_NAME", "NATIVE_LIBRARY_FOREIGN_BY_PATH",
            "TCP_HTTP_CLIENT_TASKS", "TCP_HTTP_CLIENT_SEND", "FILE_WRITE_SECURE_FOLDER", "FILE_WRITE_SECURE_DELETE",
            "FILE_WRITE_SECURE_MOVE", "FILE_WRITE_SECURE_CHANNEL")));

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

    /**
     * The class of the default file system that declares its open, delete, copy, move and link methods: Windows' own,
     * or the one Linux, macOS and the other Unix systems share.
     *
     * @return the class's name
     */
    static String fileSystemProvider() {
        final String unix = "sun.nio.fs.UnixFileSystemProvider";
        return load(unix) == null ? "sun.nio.fs.WindowsFileSystemProvider" : unix;
    }

    /** Whether a class is on this JDK and declares a method whose name starts so; any method, for empty. */
    static boolean declares(final String type, final String method) {
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
    static Class<?> load(final String type) {
        try {
            return Class.forName(type, false, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException | LinkageError notOnThisJdk) {
            return null;
        }
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
