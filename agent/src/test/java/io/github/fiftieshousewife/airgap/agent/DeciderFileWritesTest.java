package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Writes outside the project are reported by default, refused only when the settings file says so, and never
// allowed by an allow entry; the project's folders are the root, the temporary folder and the report's.
class DeciderFileWritesTest {

    private final List<Call> recorded = new ArrayList<>();

    private Decider decider(final String settings) {
        return new Decider(Settings.parse(settings), recorded::add);
    }

    @Test
    void reportsAFileWriteOutsideTheProjectAndRefusesItOnlyWhenTheSettingsSay(@TempDir final Path folder)
            throws IOException {
        final Target outside = Target.ofFile("/data/out/orders.csv");
        final Path refusing = Files.write(folder.resolve("airgap.yaml"),
                "file_writes: refuse\n".getBytes(StandardCharsets.UTF_8));
        final ReportSchema schema = new ReportSchema();

        final Throwable reported = decider(null).decide(Kind.FILE_WRITE, outside, CallContext.NONE);
        final Throwable refused = decider("settings=" + refusing).decide(Kind.FILE_WRITE, outside, CallContext.NONE);
        final Throwable inReportMode = decider("settings=" + refusing + ";mode=report")
                .decide(Kind.FILE_WRITE, outside, CallContext.NONE);

        assertAll(
                () -> assertThat(reported).as("reported by default, even in refuse mode").isNull(),
                () -> assertThat(refused).isExactlyInstanceOf(FileNotFoundException.class)
                        .hasMessage("/data/out/orders.csv (" + Refusals.REFUSED + ")"),
                () -> assertThat(inReportMode).as("the mode report lets everything go ahead").isNull(),
                () -> assertThat(recorded).extracting(Call::getOutcome)
                        .containsExactly("reported", "refused", "reported"),
                () -> assertThat(recorded).extracting(Call::getKind).containsOnly(Kind.FILE_WRITE),
                () -> assertThat(recorded).extracting(call -> schema.problems(call.toJson())).allSatisfy(
                        problems -> assertThat(problems).isEmpty()));
    }

    @Test
    void knowsTheProjectsFolders(@TempDir final Path folder) {
        final Decider decider = decider("root=" + folder + ";report=" + folder.resolve("elsewhere/calls.jsonl"));

        assertAll(
                () -> assertThat(decider.isProjects(folder.resolve("build/out.txt").toString())).isTrue(),
                () -> assertThat(decider.isProjects(System.getProperty("java.io.tmpdir") + "/x")).isTrue(),
                () -> assertThat(decider.isProjects("/data/out/orders.csv")).isFalse());
    }

    @Test
    void neverAllowsAFileWriteByAnAllowEntry() {
        final Decider decider = decider("allow=/data/out/orders.csv");

        assertThat(decider.allows(Kind.FILE_WRITE, Target.ofFile("/data/out/orders.csv"), CallContext.NONE))
                .as("folders are allowed in write_folders").isFalse();
    }
}
