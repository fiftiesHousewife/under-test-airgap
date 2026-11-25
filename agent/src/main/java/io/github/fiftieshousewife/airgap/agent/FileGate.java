package io.github.fiftieshousewife.airgap.agent;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * Where the file-write hooks send their calls. A write inside the project's folders, by the build tool's own classes,
 * or by the agent itself goes ahead unchecked; that's decided first, and quickly, since these are the JVM's busiest
 * calls. Anything else is decided as any risky call is: reported, or refused when the settings say
 * {@code file_writes: refuse}. A refused write fails as an unwritable folder would. Loaded by the bootstrap class
 * loader, like {@link Gate}. Writes through a secure directory stream come here through {@link SecureStreamGate}.
 */
public final class FileGate {

    private static final int RANDOM_ACCESS_FILE_WRITE_MODE_BIT = 2;
    private static final String AGENT = "io.github.fiftieshousewife.airgap.agent.";
    private static final String[] JDK = {"java.", "javax.", "jdk.", "sun.", "com.sun."};

    private FileGate() {
    }

    /** {@code FileOutputStream} is opening a file, by its path as given, to write or append. */
    public static void open(final String name) {
        final Throwable refusal = refusal(name);
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(refusal);
        }
    }

    /** {@code RandomAccessFile} is opening a file with the JDK's mode bits; only opening it to write is checked. */
    public static void randomAccess(final String name, final int mode) {
        if ((mode & RANDOM_ACCESS_FILE_WRITE_MODE_BIT) != 0) {
            open(name);
        }
    }

    /**
     * {@code File} is deleting, making or renaming a file or folder, which answers {@code false} when refused.
     *
     * @param file the file
     * @return true if refused, so the change is skipped
     */
    public static boolean change(final File file) {
        return file != null && refusal(file.getPath()) != null;
    }

    /** {@code File} is creating a file, which fails with {@code IOException} when refused. */
    public static void create(final File file) {
        final Throwable refusal = refusal(file.getPath());
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(new IOException(refusal.getMessage(), refusal));
        }
    }

    /** The default file system is opening a channel; one opened to write, append or delete on close is checked. */
    public static void channel(final Path path, final Set<?> options) {
        if (options != null && opensToChange(options)) {
            path(path);
        }
    }

    /**
     * The default file system is creating, deleting, moving, copying to or linking a path, which fails with
     * {@link AccessDeniedException} when refused.
     */
    public static void path(final Path path) {
        if (path == null) {
            return;
        }
        final String text = path.toString();
        final Throwable refusal = refusal(text);
        if (refusal != null) {
            final AccessDeniedException denied = new AccessDeniedException(text, null, refusal.getMessage());
            denied.initCause(refusal);
            Gate.<RuntimeException>sneakyThrow(denied);
        }
    }

    private static boolean opensToChange(final Set<?> options) {
        return options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND)
                || options.contains(StandardOpenOption.DELETE_ON_CLOSE);
    }

    // Returns what to throw if the write is refused, or null if it may go ahead
    private static Throwable refusal(final String path) {
        Gate.stopIfProbing(Kind.FILE_WRITE);
        final Decider current = Gate.currentDecider();
        if (current == null || Gate.DECIDING.get() != null || path == null) {
            return null;
        }
        final String absolute = toDecide(current, path);
        return absolute == null ? null : Gate.refusal(Kind.FILE_WRITE, Target.ofFile(absolute));
    }

    // The write's absolute path, or null if it's in the project or the agent's or build tool's own: worked out with the
    // gate closed to the agent's own work.
    private static String toDecide(final Decider current, final String path) {
        Gate.DECIDING.set(Boolean.TRUE);
        try {
            final String absolute = ProjectFolders.absolute(path);
            final boolean theirOwn = current.isProjects(absolute)
                    || isOwnWrite(current, new Throwable().getStackTrace());
            return theirOwn ? null : absolute;
        } finally {
            Gate.DECIDING.remove();
        }
    }

    // Whether the first class outside the JDK that's writing is the agent's or the build tool's.
    private static boolean isOwnWrite(final Decider decider, final StackTraceElement... frames) {
        final String writing = writingClass(frames);
        return writing.startsWith(AGENT) || decider.isBuildToolsOwn(writing);
    }

    private static boolean isTheGate(final String type) {
        return type.equals(FileGate.class.getName()) || type.equals(SecureStreamGate.class.getName());
    }

    // The class that asked for the write: the first frame that isn't the JDK's or the file gates'.
    //
    // frames: a stack, innermost first
    // Returns the class's name, or empty if the JDK alone is writing
    static String writingClass(final StackTraceElement... frames) {
        for (final StackTraceElement frame : frames) {
            final String type = frame.getClassName();
            if (!isJdks(type) && !isTheGate(type)) {
                return type;
            }
        }
        return "";
    }

    private static boolean isJdks(final String type) {
        for (final String prefix : JDK) {
            if (type.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
