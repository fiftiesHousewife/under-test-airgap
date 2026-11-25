package io.github.fiftieshousewife.airgap.agent.probes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.Hook;
import io.github.fiftieshousewife.airgap.agent.Kind;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import javax.naming.NamingException;

/**
 * The probes of the file-write hooks. Each writes in a folder that doesn't exist, so even with its hook missing it
 * fails without changing anything. A hook on several methods has a probe of each: every call must be stopped by a
 * file-write hook, or the hook counts as missing.
 */
public final class FileProbes {

    private static final String NO_SUCH_FOLDER = "airgap-self-check-no-such-folder";

    private FileProbes() {
    }

    /** @param probes where to add each file-write probe, by its hook */
    @SuppressFBWarnings(value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "PATH_TRAVERSAL_IN"},
            justification = "The probes need only the calls made, which fail before opening anything, in a folder"
                    + " of the agent's own naming")
    public static void addTo(final Map<Hook, Probe> probes) {
        probes.put(Hook.FILE_WRITE_STREAM, () -> new FileOutputStream(noSuchFolder("stream")).close());
        probes.put(Hook.FILE_WRITE_RANDOM_ACCESS, () -> new RandomAccessFile(noSuchFolder("random"), "rw").close());
        probes.put(Hook.FILE_WRITE_CHANGE, each(() -> noSuchFolder("delete").delete(),
                () -> noSuchFolder("mkdir").mkdir()));
        probes.put(Hook.FILE_WRITE_RENAME, () -> noSuchFolder("from").renameTo(noSuchFolder("to")));
        probes.put(Hook.FILE_WRITE_CREATE, () -> noSuchFolder("create").createNewFile());
        probes.put(Hook.FILE_WRITE_TEMP_FILE, () -> File.createTempFile("probe", null, noSuchFolder("temp")));
        probes.put(Hook.FILE_WRITE_CHANNEL, each(
                () -> Files.newByteChannel(noSuchPath("channel"), StandardOpenOption.WRITE).close(),
                () -> FileChannel.open(noSuchPath("file-channel"), StandardOpenOption.WRITE).close(),
                () -> AsynchronousFileChannel.open(noSuchPath("async"), StandardOpenOption.WRITE).close()));
        probes.put(Hook.FILE_WRITE_PATH, each(() -> Files.createDirectory(noSuchPath("directory")),
                () -> Files.deleteIfExists(noSuchPath("delete")),
                () -> Files.createSymbolicLink(noSuchPath("link"), noSuchPath("link-to")),
                () -> Files.createLink(noSuchPath("hard-link"), noSuchPath("hard-link-to"))));
        probes.put(Hook.FILE_WRITE_COPY, () -> Files.copy(noSuchPath("copy-from"), noSuchPath("copy-to")));
        probes.put(Hook.FILE_WRITE_MOVE, () -> Files.move(noSuchPath("move-from"), noSuchPath("move-to")));
    }

    private static Probe each(final Probe... calls) {
        return () -> {
            for (final Probe call : calls) {
                if (!isStoppedByAFileWriteHook(call)) {
                    return;
                }
            }
            throw new Gate.Reached(Kind.FILE_WRITE);
        };
    }

    private static boolean isStoppedByAFileWriteHook(final Probe call)
            throws IOException, NamingException, ReflectiveOperationException {
        try {
            call.call();
            return false;
        } catch (Gate.Reached reached) {
            return reached.getKind() == Kind.FILE_WRITE;
        }
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "The agent's own names, in the temporary folder")
    private static File noSuchFolder(final String name) {
        return new File(new File(System.getProperty("java.io.tmpdir"), NO_SUCH_FOLDER), name);
    }

    private static Path noSuchPath(final String name) {
        return noSuchFolder(name).toPath();
    }
}
