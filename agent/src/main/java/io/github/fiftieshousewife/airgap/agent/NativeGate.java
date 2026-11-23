package io.github.fiftieshousewife.airgap.agent;

/**
 * Where the native-library hooks send their calls: {@code System.loadLibrary}, {@code System.load}, their
 * {@code Runtime} versions, and JDK 22's {@code SymbolLookup.libraryLookup}. The JDK's own libraries (asked for by a
 * JDK class, or a file in the JDK's own library folder) and the build tool's own go ahead unchecked. Loaded by the
 * bootstrap class loader, like {@link Gate}.
 */
public final class NativeGate {

    private NativeGate() {
    }

    /**
     * A native library is being loaded through {@code System} or {@code Runtime}.
     *
     * @param library the library's name or path
     */
    public static void nativeLibrary(final String library) {
        final Throwable refusal = refusal(library, true);
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(refusal);
        }
    }

    /**
     * A native library is being loaded through the foreign function API, which fails to load one with
     * {@link IllegalArgumentException}.
     *
     * @param library the library's name or path
     */
    public static void foreignLibrary(final String library) {
        final Throwable refusal = refusal(library, false);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal.getMessage(), refusal);
        }
    }

    private static boolean isName(final String library) {
        return library.indexOf('/') < 0 && library.indexOf('\\') < 0;
    }

    /**
     * @param library the library's name or path
     * @param jdkFirst true if a name is looked for in the JDK's own folder first, as {@code System} and
     *     {@code Runtime} do; the foreign function API asks the operating system, which looks elsewhere
     */
    private static Throwable refusal(final String library, final boolean jdkFirst) {
        Gate.stopIfProbing(Kind.NATIVE_LIBRARY);
        final Decider current = Gate.currentDecider();
        if (current == null || Gate.DECIDING.get() != null) {
            return null;
        }
        final boolean theirOwn;
        Gate.DECIDING.set(Boolean.TRUE);
        try {
            final String asking = LibraryLoads.askingClass(new Throwable().getStackTrace());
            theirOwn = LibraryLoads.isJdks(asking) || current.isBuildToolsOwn(asking)
                    || LibraryLoads.isJdksFile(library, jdkFirst && isName(library));
        } finally {
            Gate.DECIDING.remove();
        }
        return theirOwn ? null : Gate.refusal(Kind.NATIVE_LIBRARY, Target.ofLibrary(library));
    }
}
