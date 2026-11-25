package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Writes outside the project, made for real with the agent attached and {@code file_writes: refuse} in the tests'
 * settings, are refused as an unwritable folder refuses them, and reported. Every path is in a folder at the top of
 * the file system that doesn't exist and that the tests can't create, so nothing could be written even if a hook
 * failed. Writes inside the project's folders, the temporary folder and a folder the settings list go ahead.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN",
    "DMI_HARDCODED_ABSOLUTE_FILENAME", "OS_OPEN_STREAM", "OBL_UNSATISFIED_OBLIGATION", "DM_DEFAULT_ENCODING"},
        justification = "The writes are what the agent must refuse, to a fixed folder that can't exist, so no stream"
                + " opens and no text is written; the paths are the tests' own")
class FileWritesTest {

    /** A folder that doesn't exist, outside the project and the temporary folder, which a user can't create. */
    private static final File OUTSIDE = new File("/airgap-file-writes-test.invalid");

    /** A write that fails, by throwing or answering false. */
    private interface Write {
        Object make(File file, Path path) throws IOException;
    }

    private static Arguments write(final String name, final Write write) {
        return Arguments.of(name, write);
    }

    @SuppressForbidden("FileWriter, with the default character set, is one of the writes the agent must catch")
    private static Stream<Arguments> throwingWrites() {
        return Stream.of(
                write("FileOutputStream", (file, path) -> new FileOutputStream(file)),
                write("FileOutputStream, appending", (file, path) -> new FileOutputStream(file, true)),
                write("FileWriter", (file, path) -> new FileWriter(file)),
                write("RandomAccessFile, to write", (file, path) -> new RandomAccessFile(file, "rw")),
                write("File.createNewFile", (file, path) -> file.createNewFile()),
                write("File.createTempFile", (file, path) -> File.createTempFile("airgap", ".tmp", file)),
                write("Files.write", (file, path) -> Files.write(path, new byte[] {1})),
                write("Files.newOutputStream", (file, path) -> Files.newOutputStream(path)),
                write("Files.newBufferedWriter", (file, path) -> Files.newBufferedWriter(path)),
                write("FileChannel.open", (file, path) -> FileChannel.open(path, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE)),
                write("AsynchronousFileChannel.open",
                        (file, path) -> AsynchronousFileChannel.open(path, StandardOpenOption.WRITE)),
                write("Files.createDirectory", (file, path) -> Files.createDirectory(path)),
                write("Files.delete", (file, path) -> {
                    Files.delete(path);
                    return null;
                }),
                write("Files.deleteIfExists", (file, path) -> Files.deleteIfExists(path)),
                write("Files.createSymbolicLink", (file, path) -> Files.createSymbolicLink(path, Paths.get("x"))),
                write("Files.createLink", (file, path) -> Files.createLink(path, Paths.get("x"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("throwingWrites")
    void refusesAWriteOutsideTheProjectAsAnUnwritableFolderWould(final String name, final Write write) {
        final File file = new File(OUTSIDE, name.replaceAll("[^A-Za-z]", "-"));

        final Throwable thrown = catchThrowable(() -> write.make(file, file.toPath()));

        assertAll(
                () -> assertThat(thrown).isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(linesAbout(file.getPath())).isNotEmpty().allSatisfy(line -> assertThat(line)
                        .contains("\"kind\":\"file-write\"", "\"outcome\":\"refused\"",
                                "\"caller\":\"" + FileWritesTest.class.getName())));
    }

    @Test
    void failsAsTheJdkDoesForAFileItCantWrite() {
        final File file = new File(OUTSIDE, "orders.csv");

        assertAll(
                () -> assertThatThrownBy(() -> new FileOutputStream(file)).isExactlyInstanceOf(
                        FileNotFoundException.class).hasMessage(file.getPath() + " (" + Refusals.REFUSED + ")"),
                () -> assertThatThrownBy(() -> Files.write(file.toPath(), new byte[] {1}))
                        .isExactlyInstanceOf(AccessDeniedException.class)
                        .hasMessageStartingWith(file.getPath()));
    }

    @Test
    void answersFalseForAFileChangeItRefuses() throws IOException {
        final File file = new File(OUTSIDE, "answers-false");
        final File inTemp = File.createTempFile("airgap-rename", ".txt");

        assertAll(
                () -> assertThat(file.delete()).isFalse(),
                () -> assertThat(file.mkdir()).isFalse(),
                () -> assertThat(file.mkdirs()).isFalse(),
                () -> assertThat(inTemp.renameTo(file)).as("a file moved out of the temporary folder").isFalse(),
                () -> assertThat(inTemp).exists(),
                () -> assertThat(linesAbout(file.getPath())).hasSizeGreaterThanOrEqualTo(4));
        Files.delete(inTemp.toPath());
    }

    /** It makes each missing folder in turn, so the first one it would make outside the project is refused. */
    @Test
    void refusesMakingFoldersOutsideTheProject() {
        final Path path = OUTSIDE.toPath().resolve("one").resolve("two");

        assertAll(
                () -> assertThatThrownBy(() -> Files.createDirectories(path))
                        .isInstanceOf(AccessDeniedException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(linesAbout(OUTSIDE.getPath() + "\"")).isNotEmpty());
    }

    @Test
    void refusesCopyingOrMovingAFileOutOfTheTemporaryFolder(@TempDir final Path folder) throws IOException {
        final Path inTemp = Files.write(folder.resolve("report.csv"), new byte[] {1});
        final Path copied = OUTSIDE.toPath().resolve("copied.csv");
        final Path moved = OUTSIDE.toPath().resolve("moved.csv");

        assertAll(
                () -> assertThatThrownBy(() -> Files.copy(inTemp, copied)).isInstanceOf(AccessDeniedException.class),
                () -> assertThatThrownBy(() -> Files.move(inTemp, moved)).isInstanceOf(AccessDeniedException.class),
                () -> assertThat(inTemp).exists(),
                () -> assertThat(linesAbout(copied.toString())).hasSize(1),
                () -> assertThat(linesAbout(moved.toString())).hasSize(1));
    }

    /**
     * Where the system supports it, {@code Files.newDirectoryStream} hands out a secure stream, whose methods take
     * paths relative to its folder and don't go through the file system's own delete, move or open.
     */
    @Test
    void refusesWritesThroughASecureDirectoryStream(@TempDir final Path folder) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            assumeTrue(stream instanceof SecureDirectoryStream, "this system's JDK hands out no secure streams");
            @SuppressWarnings("unchecked")
            final SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) stream;
            final Path inTemp = Files.write(folder.resolve("in-temp.txt"), new byte[] {1});
            final Path outside = OUTSIDE.toPath().resolve("secure.txt");

            secure.deleteFile(inTemp.getFileName());
            assertAll(
                    () -> assertThat(inTemp).as("a relative path, in the stream's folder, goes ahead").doesNotExist(),
                    () -> assertThatThrownBy(() -> secure.deleteFile(outside))
                            .isInstanceOf(AccessDeniedException.class).hasMessageContaining(Refusals.REFUSED),
                    () -> assertThatThrownBy(() -> secure.newByteChannel(outside,
                            Collections.singleton(StandardOpenOption.WRITE))).isInstanceOf(AccessDeniedException.class),
                    () -> assertThat(linesAbout(outside.toString())).hasSize(2));
        }
    }

    @Test
    void letsReadsGoAhead() {
        final File file = new File(OUTSIDE, "read-only");

        assertAll(
                () -> assertThatThrownBy(() -> new RandomAccessFile(file, "r")).isInstanceOf(
                        FileNotFoundException.class).hasMessageNotContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> Files.newByteChannel(file.toPath(), StandardOpenOption.READ))
                        .isInstanceOf(NoSuchFileException.class).hasMessageNotContaining(Refusals.REFUSED),
                () -> assertThat(linesAbout(file.getPath())).isEmpty());
    }

    @Test
    void letsWritesInTheProjectGoAhead(@TempDir final Path temporary) throws IOException {
        final Path inTheProject = Paths.get("build", "airgap-inside-the-project", "written.txt");
        final Path inAListedFolder = Paths.get("..", "build", "airgap-agent-test-writes", "written.txt");
        final Path inTemporary = temporary.resolve("written.txt");

        for (final Path path : new Path[] {inTheProject, inAListedFolder, inTemporary}) {
            Files.createDirectories(Objects.requireNonNull(path.getParent()));
            Files.write(path, "written".getBytes(StandardCharsets.UTF_8));
            Files.delete(path);
        }

        assertAll(
                () -> assertThat(linesAbout("airgap-inside-the-project")).isEmpty(),
                () -> assertThat(linesAbout("airgap-agent-test-writes")).isEmpty(),
                () -> assertThat(linesAbout(temporary.toString())).isEmpty());
    }

    /** Report lines whose target contains the text. */
    private static List<String> linesAbout(final String target) throws IOException {
        return Files.readAllLines(Paths.get(System.getProperty("airgap.test.report")), StandardCharsets.UTF_8)
                .stream().filter(line -> line.contains("\"kind\":\"file-write\"") && line.contains(target))
                .collect(Collectors.toList());
    }
}
