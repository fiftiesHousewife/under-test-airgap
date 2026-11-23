package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every line the agent wrote during its own tests, on every JDK, matches the report's schema. Run by the
 * {@code checkAgentTestReports} task, after the agent's tests, not with the unit tests.
 */
class AgentTestReportsMatchSchemaTest {

    /** @return every report the agent's tests wrote, on every JDK; there must be some */
    private static List<Path> reports() throws IOException {
        final List<Path> reports;
        try (Stream<Path> files = Files.walk(Path.of(System.getProperty("airgap.agentTestReports")))) {
            reports = files.filter(file -> "calls.jsonl".equals(String.valueOf(file.getFileName()))).toList();
        }
        assertThat(reports).as("reports written by the agent's tests").isNotEmpty();
        return reports;
    }

    @Test
    void everyLineWrittenOnEveryJdkMatchesTheSchema() throws IOException {
        final ReportSchema schema = new ReportSchema();
        final List<Path> reports = reports();
        for (final Path report : reports) {
            assertThat(Files.readAllLines(report)).as(report.toString()).isNotEmpty()
                    .allSatisfy(line -> assertThat(schema.problems(line)).as(line).isEmpty());
        }
    }

    /** As each test JVM ended, the agent summarised its report: the summary is beside it, and agrees with it. */
    @Test
    void everyReportHasASummaryThatMatchesItsSchemaAndItsReport() throws IOException {
        final ReportSchema schema = new ReportSchema("airgap.summarySchema");
        final List<Path> reports = reports();
        for (final Path report : reports) {
            final String summary = Files.readString(Summary.fileFor(report));
            assertAll(
                    () -> assertThat(schema.problems(summary)).as(report.toString()).isEmpty(),
                    () -> assertThat(summary).as(report.toString())
                            .isEqualTo(Summary.of("calls.jsonl", Files.readString(report))));
        }
    }
}
