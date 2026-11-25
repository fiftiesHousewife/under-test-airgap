package example.airgap;

import static example.airgap.FileWrites.OUTSIDE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

// What the file-write hooks let through, with file_writes: refuse in the tests' settings: reads anywhere, writes
// inside the project's folders, the temporary folder and a folder the settings list, and a secure directory stream's
// writes inside its own folder. FileWritesTest proves the writes outside the project are refused.
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = "OS_OPEN_STREAM",
        justification = "The reads are of a file in a folder that can't exist, so no stream opens")
class FileWritesAllowedTest {

    // Where the system supports it, Files.newDirectoryStream hands out a secure stream, whose methods take
    // paths relative to its folder and don't go through the file system's own delete, move or open.
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
                    () -> assertThat(ReportLines.fileWritesAbout(outside.toString())).hasSize(2));
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
                () -> assertThat(ReportLines.fileWritesAbout(file.getPath())).isEmpty());
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
                () -> assertThat(ReportLines.fileWritesAbout("airgap-inside-the-project")).isEmpty(),
                () -> assertThat(ReportLines.fileWritesAbout("airgap-agent-test-writes")).isEmpty(),
                () -> assertThat(ReportLines.fileWritesAbout(temporary.toString())).isEmpty());
    }
}
