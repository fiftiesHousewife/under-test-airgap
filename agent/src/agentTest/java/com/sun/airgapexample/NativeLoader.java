package com.sun.airgapexample;

/**
 * A class from the class path in a package that looks like the JDK's, as JNA's {@code com.sun.jna} does: its native
 * libraries are the project's, not the JDK's.
 */
public final class NativeLoader {

    private NativeLoader() {
    }

    /**
     * Loads a library by name.
     *
     * @param name the library
     */
    public static void load(final String name) {
        System.loadLibrary(name);
    }
}
