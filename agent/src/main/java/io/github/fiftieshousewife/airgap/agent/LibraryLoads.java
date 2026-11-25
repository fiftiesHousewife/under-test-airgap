package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Works out which class asked for a native library, and whether that class is the JDK's own. A call may pass through
// reflection and method handles on its way to the load, which don't count as asking.
final class LibraryLoads {

    private static final List<String> REFLECTION_AND_METHOD_HANDLES = Collections.unmodifiableList(Arrays.asList(
            "java.lang.reflect.", "jdk.internal.reflect.", "sun.reflect.", "java.lang.invoke."));

    private LibraryLoads() {
    }

    // The class that called System.loadLibrary, System.load, their Runtime versions or
    // SymbolLookup.libraryLookup,
    // through reflection or method handles if need be.
    //
    // frames: the stack, innermost first
    // Returns the class name, or an empty string if the stack has no such call
    // Plain loops, not lambdas: this runs inside the hook on loading a native library, where the JDK may be loading
    // the libraries its own lambdas need.
    static String askingClass(final StackTraceElement... frames) {
        final int asking = afterPassingOn(frames, firstLoadMethod(frames));
        return asking < frames.length ? frames[asking].getClassName() : "";
    }

    // Returns the first frame in a load method's class, or the stack's length
    private static int firstLoadMethod(final StackTraceElement... frames) {
        int i = 0;
        while (i < frames.length && !isLoadMethodsClass(frames[i].getClassName())) {
            i++;
        }
        return i;
    }

    // Returns the first frame from the one given that isn't a load method's, reflection's or a method handle's
    private static int afterPassingOn(final StackTraceElement[] frames, final int from) {
        int i = from;
        while (i < frames.length && isPassingOn(frames[i].getClassName())) {
            i++;
        }
        return i;
    }

    private static boolean isLoadMethodsClass(final String type) {
        return "java.lang.System".equals(type) || "java.lang.Runtime".equals(type)
                || "java.lang.foreign.SymbolLookup".equals(type);
    }

    private static boolean isPassingOn(final String type) {
        if (isLoadMethodsClass(type)) {
            return true;
        }
        for (final String prefix : REFLECTION_AND_METHOD_HANDLES) {
            if (type.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // Whether a class is the JDK's: one its own class loaders can see, the bootstrap loader and the one above the
    // class path (the platform loader, or on JDK 8 the extension loader). Its libraries are the JDK's own, loaded as
    // it needs them. Judged by loader, not package, since libraries such as JNA use com.sun. packages.
    //
    // className: the class
    // Returns true for the JDK's classes
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

    // Whether a library's file is one of the JDK's own: a name the JVM finds first in the JDK's own library folder
    // (sun.boot.library.path), or a path to a file in that folder. The JDK's code can be loaded by other class
    // loaders, as JDK 8's tools.jar is when Mockito attaches its agent, and its libraries are the JDK's own
    // all the same. A project can't put files in that folder, so its own libraries are still judged as its own.
    //
    // library: the library's name, or its path
    // byName: true for a name, which the JVM looks for in the JDK's folder first; false for a path
    // Returns true if the file is in the JDK's own library folder
    static boolean isJdksFile(final String library, final boolean byName) {
        final String folders = System.getProperty("sun.boot.library.path", "");
        for (final String folder : folders.split(File.pathSeparator)) {
            if (!folder.isEmpty() && isIn(new File(folder), library, byName)) {
                return true;
            }
        }
        return false;
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "Only looks to see whether a library the code asked for is in the JDK's own folder")
    private static boolean isIn(final File folder, final String library, final boolean byName) {
        if (byName) {
            return new File(folder, System.mapLibraryName(library)).isFile();
        }
        try {
            final File file = new File(library).getCanonicalFile();
            return file.isFile() && folder.getCanonicalFile().equals(file.getParentFile());
        } catch (IOException unreadable) {
            return false;
        }
    }

    // The highest class loader below the bootstrap loader: it sees the JDK's classes and none of the class path.
    private static ClassLoader jdksLoader() {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        while (loader != null && loader.getParent() != null) {
            loader = loader.getParent();
        }
        return loader;
    }
}
