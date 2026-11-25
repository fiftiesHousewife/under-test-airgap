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

    // A call to the target, refused unless the test says otherwise, from a line of code that depends on it.
    private static Call.CallBuilder call(final Kind kind, final String target) {
        return Call.builder().kind(kind).target(target).outcome("refused").run("test").javaVersion("25")
                .caller("com.example.Orders.save(Orders.java:" + target.length() + ")");
    }

    private static String line(final Call.CallBuilder call) {
        return call.build().toJson() + "\n";
    }

    // Five calls to three targets: the database three times, by two tests, and its name looked up once
    private static String fiveCalls() {
        return line(call(Kind.TCP, "db.example.invalid:5432").test("OrdersTest.saves"))
                + line(call(Kind.TCP, "db.example.invalid:5432").test("OrdersTest.saves"))
                + line(call(Kind.TCP, "db.example.invalid:5432").outcome("allowed").test("BillingTest.bills"))
                + line(call(Kind.HOST_LOOKUP, "db.example.invalid"))
                + line(call(Kind.TCP, "a.example.invalid:80").outcome("reported").test("AuditTest.audits"));
    }

    @Test
    void listsEachTargetOnceInOrderOfKindThenTarget() throws IOException {
        final String summary = Summary.of("calls.jsonl", fiveCalls());
        final JsonNode targets = JSON.readTree(summary).get("targets");

        assertAll(
                () -> assertThat(new ReportSchema("airgap.summarySchema").problems(summary)).isEmpty(),
                () -> assertThat(JSON.readTree(summary).get("report").asString()).isEqualTo("calls.jsonl"),
                () -> assertThat(targets.size()).as("each distinct kind and target once").isEqualTo(3),
                () -> assertThat(targets.get(0).get("target").asString()).isEqualTo("db.example.invalid"),
                () -> assertThat(targets.get(1).get("target").asString()).isEqualTo("a.example.invalid:80"),
                () -> assertThat(summary).as("one target per line, for a person to read")
                        .contains("[\n{\"kind\":\"host-lookup\"", "},\n{\"kind\":\"tcp\"").endsWith("\n]}\n"));
    }

    @Test
    void givesEachTargetItsCallsOutcomesTestsAndCallers() throws IOException {
        final JsonNode targets = JSON.readTree(Summary.of("calls.jsonl", fiveCalls())).get("targets");
        final JsonNode database = targets.get(2);

        assertAll(
                () -> assertThat(database.get("calls").asInt()).isEqualTo(3),
                () -> assertThat(database.get("outcomes").get("refused").asInt()).isEqualTo(2),
                () -> assertThat(database.get("outcomes").get("allowed").asInt()).isEqualTo(1),
                () -> assertThat(database.get("outcomes").has("reported")).isFalse(),
                () -> assertThat(database.get("tests").toString())
                        .isEqualTo("[\"BillingTest.bills\",\"OrdersTest.saves\"]"),
                () -> assertThat(database.get("callers").toString())
                        .isEqualTo("[\"com.example.Orders.save(Orders.java:23)\"]"),
                () -> assertThat(targets.get(0).has("tests")).as("no test was running").isFalse());
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

    @Test
    void listsALibraryUnpackedToRandomlyNamedFilesOnce() throws IOException {
        final String report = line(call(Kind.NATIVE_LIBRARY,
                "/tmp/sqlite-3.36.0.3-0253970c-ad25-4e40-9935-d2f7ae654918-libsqlitejdbc.jnilib"))
                + line(call(Kind.NATIVE_LIBRARY,
                        "/tmp/sqlite-3.36.0.3-03CF53BA-8140-4805-a449-12ef8fea0e2b-libsqlitejdbc.jnilib"))
                + line(call(Kind.NATIVE_LIBRARY, "C:\\Temp\\netty_transport_native_epoll_x86_641234567890123.dll"))
                + line(call(Kind.NATIVE_LIBRARY, "/opt/lib/libfoo.so.1.2"));

        final JsonNode targets = JSON.readTree(Summary.of("calls.jsonl", report)).get("targets");

        assertAll(
                () -> assertThat(targets.size()).isEqualTo(3),
                () -> assertThat(targets.get(0).get("target").asString())
                        .isEqualTo("/opt/lib/libfoo.so.1.2"),
                () -> assertThat(targets.get(1).get("target").asString())
                        .isEqualTo("/tmp/sqlite-3.36.0.3-*-libsqlitejdbc.jnilib"),
                () -> assertThat(targets.get(1).get("calls").asInt()).isEqualTo(2),
                () -> assertThat(targets.get(2).get("target").asString())
                        .isEqualTo("C:\\Temp\\netty_transport_native_epoll_x86_*.dll"));
    }

    @Test
    void groupsOnlyTheRandomPartsOfLibrariesAndPrograms() {
        assertAll(
                () -> assertThat(Summary.grouped("tcp", "12345678.example.invalid:80")).as("only libraries")
                        .isEqualTo("12345678.example.invalid:80"),
                () -> assertThat(Summary.grouped("native-library", "/tmp/12345678/libfoo.so"))
                        .as("only the file name, not its folder").isEqualTo("/tmp/12345678/libfoo.so"),
                () -> assertThat(Summary.grouped("native-library", null)).isNull(),
                () -> assertThat(Summary.grouped("process", "/var/folders/T/1795504392943-0/redis-server-2.8.19.app"))
                        .as("a program's random folder").isEqualTo("/var/folders/T/*-0/redis-server-2.8.19.app"),
                () -> assertThat(Summary.grouped("process", "git")).isEqualTo("git"),
                () -> assertThat(Summary.grouped("process", null)).isNull());
    }
}
