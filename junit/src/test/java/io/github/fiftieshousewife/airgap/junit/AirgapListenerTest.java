package io.github.fiftieshousewife.airgap.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** With the agent attached, the listener names the running test, and the report records it with each call. */
class AirgapListenerTest {

    @Test
    void namesTheRunningTest() {
        assertThat(Gate.currentTest()).isEqualTo(AirgapListenerTest.class.getName() + ".namesTheRunningTest");
    }

    @Test
    void theReportNamesTheTestBehindARefusedCall() {
        final String host = "listener-test.example.invalid";

        assertAll(
                () -> assertThatThrownBy(() -> InetAddress.getByName(host)).isInstanceOf(UnknownHostException.class),
                () -> assertThat(reportLinesAbout(host)).singleElement().asString().contains("\"test\":\""
                        + AirgapListenerTest.class.getName() + ".theReportNamesTheTestBehindARefusedCall\""));
    }

    private static List<String> reportLinesAbout(final String host) throws IOException {
        return Files.readAllLines(Path.of(System.getProperty("airgap.test.report"))).stream()
                .filter(line -> line.contains(host)).toList();
    }

    @Test
    void doesNothingWithoutTheAgent() {
        final GateBridge none = GateBridge.load("io.github.example.NoSuchGate");
        none.enterTest("ignored");
        none.allow("ignored.example.invalid");
        none.leaveTest();

        assertAll(
                () -> assertThat(none.isAttached()).isFalse(),
                () -> assertThat(GateBridge.agent().isAttached()).isTrue());
    }
}
