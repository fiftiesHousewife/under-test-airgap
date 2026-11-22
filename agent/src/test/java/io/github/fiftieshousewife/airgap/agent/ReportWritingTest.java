package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWritingTest {

    @TempDir
    Path folder;

    private static Call call(final String target) {
        return Call.builder().kind(Kind.TCP).target(target).outcome("refused").run("test").javaVersion("25")
                .caller("com.example.Orders.save(Orders.java:12)").stack(List.of("a", "b")).test("OrdersTest.saves")
                .build();
    }

    @Test
    void writesOneLineOfJsonPerCallThatMatchesTheSchema() throws IOException {
        final Path file = folder.resolve("nested/calls.jsonl");
        final JsonLinesReport report = new JsonLinesReport(file);
        report.record(call("db.example.invalid:5432"));
        report.record(call("quote\"and\\slash\nline"));

        final List<String> lines = Files.readAllLines(file);
        final ReportSchema schema = new ReportSchema();
        assertAll(
                () -> assertThat(lines).hasSize(2),
                () -> assertThat(lines).allSatisfy(line -> assertThat(schema.problems(line)).isEmpty()),
                () -> assertThat(lines.get(1)).contains("quote\\\"and\\\\slash\\nline"),
                () -> assertThat(report.getLastFailure()).isNull());
    }

    @Test
    void keepsLinesWholeWhenManyThreadsWriteAtOnce() throws InterruptedException, IOException {
        final Path file = folder.resolve("calls.jsonl");
        final JsonLinesReport report = new JsonLinesReport(file);
        final ExecutorService threads = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 200; i++) {
            final int number = i;
            threads.execute(() -> report.record(call("host" + number + ".example.invalid:1")));
        }
        threads.shutdown();
        threads.awaitTermination(30, TimeUnit.SECONDS);

        final ReportSchema schema = new ReportSchema();
        assertThat(Files.readAllLines(file)).hasSize(200)
                .allSatisfy(line -> assertThat(schema.problems(line)).isEmpty());
    }

    @Test
    void neverStopsARefusalWhenTheReportCantBeWritten() throws IOException {
        final Path notAFolder = Files.writeString(folder.resolve("taken"), "a file, not a folder");
        final JsonLinesReport report = new JsonLinesReport(notAFolder.resolve("calls.jsonl"));

        report.record(call("db.example.invalid:5432"));

        assertThat(report.getLastFailure()).isInstanceOf(IOException.class);
    }

    @Test
    void leavesOutEmptyFields() {
        final Call bare = Call.builder().kind(Kind.HOST_LOOKUP).target("x.example.invalid").outcome("refused")
                .run("build").javaVersion("8").caller("(unknown)").build();

        assertThat(bare.toJson()).doesNotContain("\"test\"", "\"stack\"", "\"allowed_by\"")
                .contains("\"run\":{\"kind\":\"build\",\"java_version\":\"8\"}");
    }

    @Test
    void escapesControlCharacters() {
        assertThat(Json.quote("tab\tcarriage\rbell\u0007")).isEqualTo("\"tab\\tcarriage\\rbell\\u0007\"");
    }
}
