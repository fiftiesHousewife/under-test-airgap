package io.github.fiftieshousewife.airgap.agent;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Where the file-write hooks send their calls. A write inside the project's folders, by the build tool's own classes,
 * or by the agent itself goes ahead unchecked; that's decided first, and quickly, since these are the JVM's busiest
 * calls. Anything else is decided as any risky call is: reported, or refused when the settings say
 * {@code file_writes: refuse}. A refused write fails as an unwritable folder would. Loaded by the bootstrap class
 * loader, like {@link Gate}.
 */
public final class FileGate {

    /** {@code RandomAccessFile}'s mode bit for opening to write, the same on every JDK. */
    private static final int READ_WRITE = 2;
    private static final String AGENT = "io.github.fiftieshousewife.airgap.agent.";
    private static final String[] JDK = {"java.", "javax.", "jdk.", "sun.", "com.sun."};

    /**
     * Each secure directory stream's folder, which the paths its methods are given are relative to. Every stream is
     * made after the hooks are in place, so each is here; a stream nobody holds any more is dropped.
     */
    private static final Map<Object, Path> STREAM_FOLDERS = Collections.synchronizedMap(new WeakHashMap<>());

    private FileGate() {
    }

    /**
     * {@code FileOutputStream} is opening a file to write or append.
     *
     * @param name the file's path, as given
     */
    public static void open(final String name) {
        final Throwable refusal = refusal(name);
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(refusal);
        }
    }

    /**
     * {@code RandomAccessFile} is opening a file; only opening it to write is checked.
     *
     * @param name the file's path, as given
     * @param mode the JDK's open mode bits
     */
    public static void randomAccess(final String name, final int mode) {
        if ((mode & READ_WRITE) != 0) {
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

    /**
     * {@code File} is creating a file, which fails with {@code IOException} when refused.
     *
     * @param file the file
     */
    public static void create(final File file) {
        final Throwable refusal = refusal(file.getPath());
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(new IOException(refusal.getMessage(), refusal));
        }
    }

    /**
     * The default file system is opening a channel; only one opened to write, append or delete on close is checked.
     *
     * @param path the file
     * @param options how it's opened
     */
    public static void channel(final Path path, final Set<?> options) {
        if (options != null && (options.contains(StandardOpenOption.WRITE)
                || options.contains(StandardOpenOption.APPEND)
                || options.contains(StandardOpenOption.DELETE_ON_CLOSE))) {
            path(path);
        }
    }

    /**
     * The default file system is creating, deleting, moving, copying to or linking a path, which fails with
     * {@link AccessDeniedException} when refused.
     *
     * @param path the path changed
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

    /**
     * A secure directory stream has been opened on a folder.
     *
     * @param stream the stream
     * @param folder its folder
     */
    public static void openedFolder(final Object stream, final Path folder) {
        if (stream != null && folder != null) {
            STREAM_FOLDERS.put(stream, folder);
        }
    }

    /**
     * A secure directory stream is deleting or moving a path in its folder.
     *
     * @param stream the stream
     * @param path the path, relative to the stream's folder unless absolute
     */
    public static void inFolder(final Object stream, final Path path) {
        path(resolvedIn(stream, path));
    }

    /**
     * A secure directory stream is moving a path from its folder to another stream's.
     *
     * @param stream the stream
     * @param arguments the path moved, the stream it moves to, and its path there
     */
    public static void secureMove(final Object stream, final Object... arguments) {
        inFolder(stream, (Path) arguments[0]);
        inFolder(arguments[1], (Path) arguments[2]);
    }

    /**
     * A secure directory stream is opening a channel in its folder.
     *
     * @param stream the stream
     * @param path the path, relative to the stream's folder unless absolute
     * @param options how it's opened
     */
    public static void channelInFolder(final Object stream, final Path path, final Set<?> options) {
        channel(resolvedIn(stream, path), options);
    }

    private static Path resolvedIn(final Object stream, final Path path) {
        final Path folder = path == null || path.isAbsolute() ? null : STREAM_FOLDERS.get(stream);
        return folder == null ? path : folder.resolve(path);
    }

    /** @return what to throw if the write is refused, or {@code null} if it may go ahead */
    private static Throwable refusal(final String path) {
        Gate.stopIfProbing(Kind.FILE_WRITE);
        final Decider current = Gate.currentDecider();
        if (current == null || Gate.DECIDING.get() != null || path == null) {
            return null;
        }
        final String absolute;
        final boolean theirOwn;
        Gate.DECIDING.set(Boolean.TRUE);
        try {
            absolute = ProjectFolders.absolute(path);
            theirOwn = current.isProjects(absolute) || isOwnWrite(current, new Throwable().getStackTrace());
        } finally {
            Gate.DECIDING.remove();
        }
        return theirOwn ? null : Gate.refusal(Kind.FILE_WRITE, Target.ofFile(absolute));
    }

    /** Whether the first class outside the JDK that's writing is the agent's or the build tool's. */
    private static boolean isOwnWrite(final Decider decider, final StackTraceElement... frames) {
        final String writing = writingClass(frames);
        return writing.startsWith(AGENT) || decider.isBuildToolsOwn(writing);
    }

    private static boolean isTheGate(final String type) {
        return type.equals(FileGate.class.getName());
    }

    /**
     * The class that asked for the write: the first frame that isn't the JDK's or this gate's.
     *
     * @param frames a stack, innermost first
     * @return the class's name, or empty if the JDK alone is writing
     */
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
