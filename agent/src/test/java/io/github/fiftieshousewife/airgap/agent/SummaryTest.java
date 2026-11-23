package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SummaryTest {

    private static final JsonMapper JSON = new JsonMapper();

    /** A call to the target, refused unless the test says otherwise, from a line of code that depends on it. */
    private static Call.CallBuilder call(final Kind kind, final String target) {
        return Call.builder().kind(kind).target(target).outcome("refused").run("test").javaVersion("25")
                .caller("com.example.Orders.save(Orders.java:" + target.length() + ")");
    }

    private static String line(final Call.CallBuilder call) {
        return call.build().toJson() + "\n";
    }

    @Test
    void listsEachTargetOnceWithItsCallsOutcomesTestsAndCallers() throws IOException {
        final String report = line(call(Kind.TCP, "db.example.invalid:5432").test("OrdersTest.saves"))
                + line(call(Kind.TCP, "db.example.invalid:5432").test("OrdersTest.saves"))
                + line(call(Kind.TCP, "db.example.invalid:5432").outcome("allowed").test("BillingTest.bills"))
                + line(call(Kind.HOST_LOOKUP, "db.example.invalid"))
                + line(call(Kind.TCP, "a.example.invalid:80").outcome("reported").test("AuditTest.audits"));

        final String summary = Summary.of("calls.jsonl", report);
        final JsonNode targets = JSON.readTree(summary).get("targets");
        final JsonNode database = targets.get(2);

        assertAll(
                () -> assertThat(new ReportSchema("airgap.summarySchema").problems(summary)).isEmpty(),
                () -> assertThat(JSON.readTree(summary).get("report").asString()).isEqualTo("calls.jsonl"),
                () -> assertThat(targets.size()).as("each distinct kind and target once").isEqualTo(3),
                () -> assertThat(targets.get(0).get("target").asString()).as("in order of kind, then target")
                        .isEqualTo("db.example.invalid"),
                () -> assertThat(targets.get(1).get("target").asString()).isEqualTo("a.example.invalid:80"),
                () -> assertThat(database.get("calls").asInt()).isEqualTo(3),
                () -> assertThat(database.get("outcomes").get("refused").asInt()).isEqualTo(2),
                () -> assertThat(database.get("outcomes").get("allowed").asInt()).isEqualTo(1),
                () -> assertThat(database.get("outcomes").has("reported")).isFalse(),
                () -> assertThat(database.get("tests").toString())
                        .isEqualTo("[\"BillingTest.bills\",\"OrdersTest.saves\"]"),
                () -> assertThat(database.get("callers").toString())
                        .isEqualTo("[\"com.example.Orders.save(Orders.java:23)\"]"),
                () -> assertThat(targets.get(0).has("tests")).as("no test was running").isFalse(),
                () -> assertThat(summary).as("one target per line, for a person to read")
                        .contains("[\n{\"kind\":\"host-lookup\"", "},\n{\"kind\":\"tcp\"").endsWith("\n]}\n"));
    }

    @Test
    void skipsLinesThatArentWhole() throws IOException {
        final String report = "\n" + line(call(Kind.UDP, "192.0.2.1:53").test("DnsTest.asks"))
                + "{\"kind\":\"tcp\",\"target\":\"cut.exam\n{\"kind\":\"tcp\"}\n"
                + "{\"target\":\"no-kind\",\"outcome\":\"refused\"}\n"
                + "{\"kind\":\"tcp\",\"target\":\"no-outcome.example.invalid:1\"}\n";

        final JsonNode targets = JSON.readTree(Summary.of("calls.jsonl", report)).get("targets");

        assertAll(
                () -> assertThat(targets.size()).isEqualTo(1),
                () -> assertThat(targets.get(0).get("target").asString()).isEqualTo("192.0.2.1:53"),
                () -> assertThat(targets.get(0).has("outcomes")).isTrue());
    }

    @Test
    void summarisesAnEmptyReportAsNoTargets() throws IOException {
        final String summary = Summary.of("calls.jsonl", "");

        assertAll(
                () -> assertThat(summary).isEqualTo("{\"report\":\"calls.jsonl\",\"targets\":[\n]}\n"),
                () -> assertThat(new ReportSchema("airgap.summarySchema").problems(summary)).isEmpty());
    }

    @Test
    void sitsBesideItsReport() {
        assertAll(
                () -> assertThat(Summary.fileFor(Path.of("build/airgap/test/calls.jsonl")))
                        .isEqualTo(Path.of("build/airgap/test/calls-summary.json")),
                () -> assertThat(Summary.fileFor(Path.of("calls.log"))).isEqualTo(Path.of("calls.log-summary.json")));
    }
}
