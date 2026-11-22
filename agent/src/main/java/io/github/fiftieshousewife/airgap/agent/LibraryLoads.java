package io.github.fiftieshousewife.airgap.agent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Works out which class asked for a native library, and whether that class is the JDK's own. */
final class LibraryLoads {

    /** Classes a call passes through on its way to the load: reflection and method handles. */
    private static final List<String> PASSING_ON = Collections.unmodifiableList(Arrays.asList(
            "java.lang.reflect.", "jdk.internal.reflect.", "sun.reflect.", "java.lang.invoke."));

    private LibraryLoads() {
    }

    /**
     * The class that called {@code System.loadLibrary}, {@code System.load} or their {@code Runtime} versions,
     * through reflection or method handles if need be.
     *
     * @param frames the stack, innermost first
     * @return the class name, or an empty string if the stack has no such call
     */
    static String askingClass(final StackTraceElement... frames) {
        int i = 0;
        while (i < frames.length && !isLoadMethodsClass(frames[i].getClassName())) {
            i++;
        }
        while (i < frames.length && isPassingOn(frames[i].getClassName())) {
            i++;
        }
        return i < frames.length ? frames[i].getClassName() : "";
    }

    private static boolean isLoadMethodsClass(final String type) {
        return "java.lang.System".equals(type) || "java.lang.Runtime".equals(type);
    }

    private static boolean isPassingOn(final String type) {
        if (isLoadMethodsClass(type)) {
            return true;
        }
        for (final String prefix : PASSING_ON) {
            if (type.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a class is the JDK's: one its own class loaders can see, the bootstrap loader and the one above the
     * class path (the platform loader, or on JDK 8 the extension loader). Its libraries are the JDK's own, loaded as
     * it needs them. Judged by loader, not package, since libraries such as JNA use {@code com.sun.} packages.
     *
     * @param className the class
     * @return true for the JDK's classes
     */
    static boolean isJdks(final String className) {
        if (className.isEmpty()) {
            return false;
        }
        try {
            Class.forName(className, false, jdksLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError notTheJdks) {
            return false;
        }
    }

    /** The highest class loader below the bootstrap loader: it sees the JDK's classes and none of the class path. */
    private static ClassLoader jdksLoader() {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        while (loader != null && loader.getParent() != null) {
            loader = loader.getParent();
        }
        return loader;
    }
}
