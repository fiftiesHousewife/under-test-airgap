package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The summary the report writes beside itself, calls-summary.json: rewritten whole, removed when it would be wrong,
// and, when several JVMs share one report, written by the last to end from every JVM's lines.
class SummaryWritingTest {

    @TempDir
    Path folder;

    @Test
    void writesTheSummaryBesideTheReport() throws IOException {
        final Path file = folder.resolve("calls.jsonl");
        final JsonLinesReport report = new JsonLinesReport(file);
        report.record(ReportWritingTest.call("db.example.invalid:5432"));
        report.record(ReportWritingTest.call("db.example.invalid:5432"));
        report.record(ReportWritingTest.call("cache.example.invalid:6379"));

        report.summarise();

        final String summary = Files.readString(folder.resolve("calls-summary.json"));
        assertAll(
                () -> assertThat(new ReportSchema("airgap.summarySchema").problems(summary)).isEmpty(),
                () -> assertThat(summary).isEqualTo(Summary.of("calls.jsonl", Files.readString(file))),
                () -> assertThat(summary).contains("\"target\":\"db.example.invalid:5432\",\"calls\":2"),
                () -> assertThat(folder.resolve("calls-summary.json.tmp")).doesNotExist());
    }

    @Test
    void removesAnOldSummaryWhenThereIsNoReport() throws IOException {
        final Path old = Files.writeString(folder.resolve("calls-summary.json"), "{\"report\":\"calls.jsonl\"}");

        new JsonLinesReport(folder.resolve("calls.jsonl")).summarise();

        assertThat(old).as("no report means no calls, so no summary").doesNotExist();
    }

    @Test
    void removesTheSummaryWhenItCantBeWritten() throws IOException {
        final Path file = folder.resolve("calls.jsonl");
        final JsonLinesReport report = new JsonLinesReport(file);
        report.record(ReportWritingTest.call("db.example.invalid:5432"));
        final Path old = Files.writeString(folder.resolve("calls-summary.json"), "{\"report\":\"calls.jsonl\"}");
        Files.createDirectories(folder.resolve("calls-summary.json.tmp/in-the-way"));

        report.summarise();

        assertAll(
                () -> assertThat(old).as("a wrong summary is worse than none").doesNotExist(),
                () -> assertThat(Files.readAllLines(file)).as("the report itself is untouched").hasSize(1));
    }

    // Forked test workers share one report: each writes its lines, then summarises as it ends.
    @Test
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "Test code: starts this JDK's own java")
    void keepsLinesWholeWhenSeveralJvmsWriteAtOnceAndTheLastToEndSummarisesThemAll()
            throws IOException, InterruptedException {
        final Path file = folder.resolve("calls.jsonl");
        final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        final List<Process> jvms = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            jvms.add(new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    WritesToAReport.class.getName(), file.toString(), "50", "jvm" + i).inheritIO().start());
        }
        for (final Process jvm : jvms) {
            assertThat(jvm.waitFor(60, TimeUnit.SECONDS) && jvm.exitValue() == 0).as("a JVM wrote its lines").isTrue();
        }

        final ReportSchema schema = new ReportSchema();
        final String summary = Files.readString(folder.resolve("calls-summary.json"));
        assertAll(
                () -> assertThat(Files.readAllLines(file)).hasSize(200)
                        .allSatisfy(line -> assertThat(schema.problems(line)).isEmpty()),
                () -> assertThat(summary).as("the last JVM to end saw every JVM's lines")
                        .isEqualTo(Summary.of("calls.jsonl", Files.readString(file))),
                () -> assertThat(summary.split("\"calls\":1,", -1)).hasSize(201));
    }
}
