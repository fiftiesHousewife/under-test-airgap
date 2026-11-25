package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME", justification = "The paths are examples, never opened")
class ProjectFoldersTest {

    @TempDir
    Path temporary;

    private static ProjectFolders folders(final String... paths) {
        return new ProjectFolders(Arrays.asList(paths), false);
    }

    @Test
    void containsWhatIsInsideAFolderAndNothingElse() {
        final ProjectFolders folders = folders("/work/orders", "/tmp/");

        assertAll(
                () -> assertThat(folders.contains("/work/orders/build/out.txt")).isTrue(),
                () -> assertThat(folders.contains("/work/orders")).as("the folder itself").isTrue(),
                () -> assertThat(folders.contains("/tmp/a")).as("a folder given with a separator").isTrue(),
                () -> assertThat(folders.contains("/work/orders-old/out.txt")).as("a neighbour").isFalse(),
                () -> assertThat(folders.contains("/work/out.txt")).isFalse(),
                () -> assertThat(folders.contains("/data/out/orders.csv")).isFalse(),
                () -> assertThat(folders.contains("/WORK/orders/out.txt")).as("case counts on Unix").isFalse(),
                () -> assertThat(folders.getPrefixes()).as("each once").containsOnlyOnce("/tmp/"));
    }

    @Test
    void countsTheNullDeviceAndStandardStreamsAsNoFile() {
        final ProjectFolders folders = folders("/work/orders");

        assertAll(
                () -> assertThat(folders.contains("/dev/null")).isTrue(),
                () -> assertThat(folders.contains("/dev/stdout")).isTrue(),
                () -> assertThat(folders.contains("/dev/stderr")).isTrue(),
                () -> assertThat(folders.contains("/dev/tty")).isTrue(),
                () -> assertThat(folders.contains("/dev/fd/2")).isTrue(),
                () -> assertThat(folders.contains("NUL")).as("Windows' null device").isTrue(),
                () -> assertThat(folders.contains("/dev/sda")).as("a disk is no stream").isFalse());
    }

    @Test
    void ignoresCaseWhereTheFileSystemDoes() {
        final ProjectFolders folders = new ProjectFolders(Collections.singletonList("/Work/Orders"), true);

        assertAll(
                () -> assertThat(folders.contains("/work/orders/build/out.txt")).isTrue(),
                () -> assertThat(folders.contains("/WORK/ORDERS")).isTrue(),
                () -> assertThat(folders.contains("/Data/out.csv")).isFalse());
    }

    // On macOS, /var and /tmp are links to /private/var and /private/tmp: a folder counts in either form.
    @Test
    void countsAFolderInTheFormGivenAndThroughItsLinks() throws IOException {
        final Path real = Files.createDirectory(temporary.resolve("real"));
        final Path link = Files.createSymbolicLink(temporary.resolve("link"), real);

        final ProjectFolders throughTheLink = ProjectFolders.of(Collections.singletonList(link.toString()));

        assertAll(
                () -> assertThat(throughTheLink.contains(link.resolve("out.txt").toString())).isTrue(),
                () -> assertThat(throughTheLink.contains(real.toRealPath().resolve("out.txt").toString())).isTrue());
    }

    @Test
    void makesAPathAbsoluteAsTheJdkDoesAndWorksOutDotsFromTheText() {
        final String here = new File("").getAbsolutePath();

        assertAll(
                () -> assertThat(ProjectFolders.absolute("build/out.txt"))
                        .isEqualTo(here + File.separator + "build" + File.separator + "out.txt"),
                () -> assertThat(ProjectFolders.absolute("/work/orders/../../data/out.csv"))
                        .isEqualTo(new File("/data/out.csv").getAbsolutePath()),
                () -> assertThat(ProjectFolders.absolute("/work/./orders/out.txt"))
                        .isEqualTo(new File("/work/orders/out.txt").getAbsolutePath()),
                () -> assertThat(ProjectFolders.absolute("/work/a\u0000b")).as("kept as it is, if unreadable")
                        .isEqualTo(new File("/work/a\u0000b").getAbsolutePath()));
    }
}
