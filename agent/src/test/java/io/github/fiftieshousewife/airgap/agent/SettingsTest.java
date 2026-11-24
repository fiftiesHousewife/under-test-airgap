package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

    @TempDir
    Path folder;

    @Test
    void hasSafeDefaults() {
        final Settings settings = Settings.parse(null);

        assertAll(
                () -> assertThat(settings.getMode()).isEqualTo(Mode.REFUSE),
                () -> assertThat(settings.getReport()).isEqualTo(Path.of("build", "airgap", "calls.jsonl")),
                () -> assertThat(settings.getAllowlist().getEntries()).isEmpty(),
                () -> assertThat(settings.getRun()).isEqualTo("test"),
                () -> assertThat(settings.getProject()).isEmpty(),
                () -> assertThat(settings.getWithoutHooks()).isEmpty(),
                () -> assertThat(Settings.parse("  ").getMode()).isEqualTo(Mode.REFUSE));
    }

    @Test
    void readsEverySetting() {
        final Settings settings = Settings.parse(" mode=Report ; report=out/calls.jsonl;allow=a.example:1, process:git;"
                + "run=application;project=com.example.;withoutHooks=tcp-socket;;");

        assertAll(
                () -> assertThat(settings.getMode()).isEqualTo(Mode.REPORT),
                () -> assertThat(settings.getReport()).isEqualTo(Path.of("out/calls.jsonl")),
                () -> assertThat(settings.getAllowlist().getEntries()).containsExactly("a.example:1", "process:git"),
                () -> assertThat(settings.getRun()).isEqualTo("application"),
                () -> assertThat(settings.getProject()).containsExactly("com.example."),
                () -> assertThat(settings.getWithoutHooks()).containsExactly("tcp-socket"));
    }

    @Test
    void addsTheCommandLinesPackagesToTheFiles() throws IOException {
        final Path file = Files.write(folder.resolve("airgap.yaml"),
                "packages: [com.example.orders.]\n".getBytes(StandardCharsets.UTF_8));

        assertAll(
                () -> assertThat(Settings.parse("settings=" + file).getProject())
                        .containsExactly("com.example.orders."),
                () -> assertThat(Settings.parse("settings=" + file + ";project=com.example.billing.").getProject())
                        .containsExactly("com.example.orders.", "com.example.billing."));
    }

    @Test
    void namesTheFoldersWritesGoAheadIn() throws IOException {
        final Path file = Files.write(folder.resolve("airgap.yaml"), ("file_writes: refuse\nwrite_folders:\n"
                + "  - folder: ../shared/out\n    reason: a\n  - folder: ~/.m2/repository\n    reason: b\n"
                + "  - folder: " + folder.resolve("absolute") + "\n    reason: c\n").getBytes(StandardCharsets.UTF_8));
        final Path root = folder.resolve("root");

        final Settings settings = Settings.parse("settings=" + file + ";root=" + root + ";report=" + folder
                .resolve("reports").resolve("calls.jsonl"));

        assertAll(
                () -> assertThat(settings.refusesFileWrites()).isTrue(),
                () -> assertThat(settings.getProjectFolders()).containsExactly(root.toString(),
                        System.getProperty("java.io.tmpdir"), folder.resolve("reports").toString(),
                        root.resolve("../shared/out").toString(),
                        Path.of(System.getProperty("user.home"), ".m2/repository").toString(),
                        folder.resolve("absolute").toString()),
                () -> assertThat(Settings.parse(null).refusesFileWrites()).as("reported by default").isFalse(),
                () -> assertThat(Settings.parse(null).getProjectFolders().get(0))
                        .as("the folder the JVM starts in, by default").isEqualTo(System.getProperty("user.dir")));
    }

    @Test
    void refusesMistakesRatherThanIgnoringThem() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("allwo=a.example"))
                        .withMessageContaining("unknown setting 'allwo'"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("refuse"))
                        .withMessageContaining("key=value"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("=refuse"))
                        .withMessageContaining("key=value"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("mode=sometimes")));
    }

    @Test
    void readsTheProjectsSettingsFileAndLetsTheCommandLineAddToIt() throws IOException {
        final Path file = Files.writeString(folder.resolve("airgap.yaml"), "mode: report\nallow:\n"
                + "  - target: localhost:8089\n    reason: WireMock\n");

        final Settings fromFile = Settings.parse("settings=" + file);
        final Settings both = Settings.parse("allow=process:git;settings=" + file + ";mode=refuse");

        assertAll(
                () -> assertThat(fromFile.getMode()).isEqualTo(Mode.REPORT),
                () -> assertThat(fromFile.getAllowlist().getEntries()).containsExactly("localhost:8089"),
                () -> assertThat(both.getMode()).as("the command line wins").isEqualTo(Mode.REFUSE),
                () -> assertThat(both.getAllowlist().getEntries()).containsExactly("localhost:8089", "process:git"),
                () -> assertThat(Settings.parse("settings=" + Files.writeString(folder.resolve("no-mode.yaml"),
                        "allow: []\n")).getMode()).as("the default, when the file sets none").isEqualTo(Mode.REFUSE));
    }

    @Test
    void stopsOnASettingsFileThatsMissingOrWrong() throws IOException {
        final Path wrong = Files.writeString(folder.resolve("airgap.yaml"), "allow:\n  - localhost:8089\n");
        final Path missing = folder.resolve("missing.yaml");

        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("settings=" + wrong))
                        .withMessageStartingWith("airgap agent: the settings file " + wrong
                                + " is wrong: allow entry 1")
                        .withMessageEndingWith("Its schema is schema/airgap-settings.schema.json"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("settings=" + missing))
                        .withMessageContaining("can't read the settings file " + missing));
    }

    /** Run where a project's tests run, the agent finds testing/airgap.yaml without being told. */
    @Test
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "Test code: starts this JDK's own java")
    void readsTestingAirgapYamlInTheWorkingFolderByDefault() throws IOException, InterruptedException {
        Files.createDirectories(folder.resolve("testing"));
        Files.writeString(folder.resolve("testing/airgap.yaml"), "mode: off\n");
        final Process jvm = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), PrintsTheMode.class.getName())
                .directory(folder.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        final String output = new String(jvm.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertThat(jvm.waitFor(60, TimeUnit.SECONDS) && jvm.exitValue() == 0).as("the JVM ended")
                        .isTrue(),
                () -> assertThat(output).isEqualTo("OFF"));
    }
}
