package io.github.fiftieshousewife.airgap.agent;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Where the hooks in the JDK's secure directory stream (on Unix) send their calls. A stream's methods are given paths
 * relative to its folder, so each stream's folder is kept while anything holds the stream; every stream is made after
 * the hooks are in place, so each is known. Each write is then decided by {@link FileGate}, by its absolute path.
 */
public final class SecureStreamGate {

    private static final Map<Object, Path> SECURE_STREAM_FOLDERS = Collections.synchronizedMap(new WeakHashMap<>());

    private SecureStreamGate() {
    }

    /**
     * A secure directory stream has been opened on a folder.
     *
     * @param stream the stream
     * @param folder its folder
     */
    public static void openedFolder(final Object stream, final Path folder) {
        if (stream != null && folder != null) {
            SECURE_STREAM_FOLDERS.put(stream, folder);
        }
    }

    /**
     * A secure directory stream is deleting or moving a path in its folder.
     *
     * @param stream the stream
     * @param path the path, relative to the stream's folder unless absolute
     */
    public static void inFolder(final Object stream, final Path path) {
        FileGate.path(resolvedIn(stream, path));
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
        FileGate.channel(resolvedIn(stream, path), options);
    }

    private static Path resolvedIn(final Object stream, final Path path) {
        final Path folder = path == null || path.isAbsolute() ? null : SECURE_STREAM_FOLDERS.get(stream);
        return folder == null ? path : folder.resolve(path);
    }
}
