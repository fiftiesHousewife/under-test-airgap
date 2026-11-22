package io.github.fiftieshousewife.airgap.agent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Works out which class asked for a native library, and whether that class is the JDK's own. */
final class LibraryLoads {

    private static final List<String> JDK = Collections.unmodifiableList(Arrays.asList(
            "java.", "javax.", "jdk.", "sun.", "com.sun."));

    private LibraryLoads() {
    }

    /**
     * The class that called {@code System.loadLibrary}, {@code System.load} or their {@code Runtime} versions.
     *
     * @param frames the stack, innermost first
     * @return the class name, or an empty string if the stack has no such call
     */
    static String askingClass(final StackTraceElement... frames) {
        for (int i = 0; i < frames.length - 1; i++) {
            final String type = frames[i].getClassName();
            if ("java.lang.System".equals(type) || "java.lang.Runtime".equals(type)) {
                return frames[i + 1].getClassName();
            }
        }
        return "";
    }

    /**
     * Whether a class is the JDK's: its libraries are the JDK's own, loaded as it needs them (on JDK 8, through
     * {@code System.loadLibrary} itself).
     *
     * @param className the class
     * @return true for the JDK's packages
     */
    static boolean isJdks(final String className) {
        for (final String prefix : JDK) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
