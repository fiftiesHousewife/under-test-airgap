package example.airgap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

// The writes the file-write tests make for real, with the agent attached and file_writes: refuse in the tests'
// settings. OUTSIDE is a folder at the top of the file system that doesn't exist and that a user can't create, outside
// the project and the temporary folder, so nothing could be written even if a hook failed.
@SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "DMI_HARDCODED_ABSOLUTE_FILENAME"},
        justification = "The writes are what the agent must refuse, to a fixed folder that can't exist, so no text is"
                + " written")
final class FileWrites {

    static final File OUTSIDE = new File("/airgap-file-writes-test.invalid");

    private FileWrites() {
    }

    // A write that fails, by throwing or answering false.
    interface Write {
        Object make(File file, Path path) throws IOException, ReflectiveOperationException;
    }

    private static Arguments write(final String name, final Write write) {
        return Arguments.of(name, write);
    }

    // Writes through streams, files that open for writing, and channels
    static Stream<Arguments> streamWrites() {
        return Stream.of(
                write("FileOutputStream", (file, path) -> new FileOutputStream(file)),
                write("FileOutputStream, appending", (file, path) -> new FileOutputStream(file, true)),
                // By reflection: FileWriter(File) uses the default character set, which the forbidden-API check
                // refuses, and it's one of the writes the agent must catch.
                write("FileWriter", (file, path) -> newFileWriter(file)),
                write("RandomAccessFile, to write", (file, path) -> new RandomAccessFile(file, "rw")),
                write("Files.write", (file, path) -> Files.write(path, new byte[] {1})),
                write("Files.newOutputStream", (file, path) -> Files.newOutputStream(path)),
                write("Files.newBufferedWriter", (file, path) -> Files.newBufferedWriter(path)),
                write("FileChannel.open", (file, path) -> FileChannel.open(path, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE)),
                write("AsynchronousFileChannel.open",
                        (file, path) -> AsynchronousFileChannel.open(path, StandardOpenOption.WRITE)));
    }

    // Writes that make, delete or link a file or folder
    static Stream<Arguments> fileSystemWrites() {
        return Stream.of(
                write("File.createNewFile", (file, path) -> file.createNewFile()),
                write("File.createTempFile", (file, path) -> File.createTempFile("airgap", ".tmp", file)),
                write("Files.createDirectory", (file, path) -> Files.createDirectory(path)),
                write("Files.delete", (file, path) -> {
                    Files.delete(path);
                    return null;
                }),
                write("Files.deleteIfExists", (file, path) -> Files.deleteIfExists(path)),
                write("Files.createSymbolicLink", (file, path) -> Files.createSymbolicLink(path, Paths.get("x"))),
                write("Files.createLink", (file, path) -> Files.createLink(path, Paths.get("x"))));
    }

    // FileWriter(File), made by reflection: it uses the default character set, which the forbidden-API check refuses,
    // and it's one of the writes the agent must catch. Its own failure is thrown as itself.
    private static Object newFileWriter(final File file) throws IOException, ReflectiveOperationException {
        try {
            return FileWriter.class.getConstructor(File.class).newInstance(file);
        } catch (InvocationTargetException made) {
            final Throwable cause = made.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw made;
        }
    }

}
