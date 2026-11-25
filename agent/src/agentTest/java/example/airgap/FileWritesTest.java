package example.airgap;

import static example.airgap.FileWrites.OUTSIDE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// Writes outside the project, made for real with the agent attached and file_writes: refuse in the tests'
// settings, are refused as an unwritable folder refuses them, and reported (see FileWrites for the folder they're
// made in). FileWritesAllowedTest proves what goes ahead.
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "OS_OPEN_STREAM", "OBL_UNSATISFIED_OBLIGATION"},
        justification = "The writes are what the agent must refuse, to a fixed folder that can't exist, so no stream"
                + " opens; the paths are the tests' own")
class FileWritesTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource({"example.airgap.FileWrites#streamWrites", "example.airgap.FileWrites#fileSystemWrites"})
    void refusesAWriteOutsideTheProjectAsAnUnwritableFolderWould(final String name, final FileWrites.Write write) {
        final File file = new File(OUTSIDE, name.replaceAll("[^A-Za-z]", "-"));

        final Throwable thrown = catchThrowable(() -> write.make(file, file.toPath()));

        assertAll(
                () -> assertThat(thrown).isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(ReportLines.fileWritesAbout(file.getPath())).isNotEmpty().allSatisfy(line ->
                        assertThat(line).contains("\"kind\":\"file-write\"", "\"outcome\":\"refused\"",
                                "\"caller\":\"" + FileWrites.class.getName() + ".")));
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
                () -> assertThat(ReportLines.fileWritesAbout(file.getPath())).hasSizeGreaterThanOrEqualTo(4));
        Files.delete(inTemp.toPath());
    }

    // It makes each missing folder in turn, so the first one it would make outside the project is refused.
    @Test
    void refusesMakingFoldersOutsideTheProject() {
        final Path path = OUTSIDE.toPath().resolve("one").resolve("two");

        assertAll(
                () -> assertThatThrownBy(() -> Files.createDirectories(path))
                        .isInstanceOf(AccessDeniedException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(ReportLines.fileWritesAbout(OUTSIDE.getPath() + "\"")).isNotEmpty());
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
                () -> assertThat(ReportLines.fileWritesAbout(copied.toString())).hasSize(1),
                () -> assertThat(ReportLines.fileWritesAbout(moved.toString())).hasSize(1));
    }
}
