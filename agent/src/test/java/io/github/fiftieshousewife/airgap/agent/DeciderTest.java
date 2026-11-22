package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeciderTest {

    private final List<Call> recorded = new ArrayList<>();
    private ReportSchema schema;

    @BeforeEach
    void readSchema() throws IOException {
        schema = new ReportSchema();
    }

    private Decider decider(final String settings) {
        return new Decider(Settings.parse(settings), recorded::add);
    }

    private static Target remote(final String host, final int port) {
        return Target.ofAddress(InetSocketAddress.createUnresolved(host, port));
    }

    private static Target documentationAddress(final int port) throws UnknownHostException {
        return Target.ofAddress(InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), port);
    }

    @Test
    void refusesEachKindWithTheExceptionAnUnpluggedMachineWouldThrow() {
        final Decider decider = decider(null);

        assertAll(
                () -> assertThat(decider.decide(Kind.HOST_LOOKUP, Target.ofLookup("db.example.invalid"),
                        CallContext.NONE)).isInstanceOf(UnknownHostException.class),
                () -> assertThat(decider.decide(Kind.TCP, documentationAddress(5432), CallContext.NONE))
                        .isInstanceOf(ConnectException.class),
                () -> assertThat(decider.decide(Kind.UDP, documentationAddress(53), CallContext.NONE))
                        .isInstanceOf(PortUnreachableException.class),
                () -> assertThat(decider.decide(Kind.TCP, remote("db.example.invalid", 5432), CallContext.NONE))
                        .as("an address nobody looked up fails as the lookup would")
                        .isInstanceOf(UnknownHostException.class),
                () -> assertThat(decider.decide(Kind.PROCESS, Target.ofCommand(List.of("curl")), CallContext.NONE))
                        .isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(recorded).extracting(Call::getOutcome).containsOnly("refused").hasSize(5));
    }

    @Test
    void allowsTheMachineItselfWithoutRecordingIt() {
        assertAll(
                () -> assertThat(decider(null).decide(Kind.TCP, remote("localhost", 8080), CallContext.NONE)).isNull(),
                () -> assertThat(recorded).isEmpty());
    }

    @Test
    void allowsAndRecordsWhatTheSettingsOrTheTestAllow() {
        final Decider decider = decider("allow=wiremock.example.invalid:8089");
        final CallContext test = new CallContext("OrdersTest.places", new Allowlist(List.of("db.example.invalid")));

        assertAll(
                () -> assertThat(decider.decide(Kind.TCP, remote("wiremock.example.invalid", 8089), CallContext.NONE))
                        .isNull(),
                () -> assertThat(decider.decide(Kind.TCP, remote("db.example.invalid", 5432), test)).isNull(),
                () -> assertThat(recorded).extracting(Call::getAllowedBy).containsExactly("settings", "test"),
                () -> assertThat(recorded).extracting(Call::getTest).containsExactly(null, "OrdersTest.places"),
                () -> assertThat(decider.allows(Kind.TCP, remote("other.example.invalid", 1), test)).isFalse());
    }

    @Test
    void inReportModeLetsEveryCallThroughAndRecordsIt() {
        final Decider decider = decider("mode=report;run=build");

        assertAll(
                () -> assertThat(decider.decide(Kind.TCP, remote("db.example.invalid", 5432), CallContext.NONE))
                        .isNull(),
                () -> assertThat(recorded).singleElement().satisfies(call -> assertAll(
                        () -> assertThat(call.getOutcome()).isEqualTo("reported"),
                        () -> assertThat(call.getRun()).isEqualTo("build"))));
    }

    @Test
    void namesTheCallerAndWritesLinesThatMatchTheSchema() {
        decider("project=io.github.fiftieshousewife.airgap.agent.DeciderTest")
                .decide(Kind.HOST_LOOKUP, Target.ofLookup("db.example.invalid"), CallContext.NONE);

        final Call call = recorded.get(0);
        assertAll(
                () -> assertThat(call.getCaller()).startsWith(DeciderTest.class.getName() + "."),
                () -> assertThat(call.getStack()).isNotEmpty().hasSizeLessThanOrEqualTo(12),
                () -> assertThat(schema.problems(call.toJson())).isEmpty());
    }
}
