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
    void theReportNamesTheTestBehindARefusedCall() throws IOException {
        final String host = "listener-test.example.invalid";
        assertThatThrownBy(() -> InetAddress.getByName(host)).isInstanceOf(UnknownHostException.class);

        final List<String> lines = Files.readAllLines(Path.of(System.getProperty("airgap.test.report"))).stream()
                .filter(line -> line.contains(host)).toList();
        assertAll(
                () -> assertThat(lines).hasSize(1),
                () -> assertThat(lines.get(0)).contains("\"test\":\"" + AirgapListenerTest.class.getName()
                        + ".theReportNamesTheTestBehindARefusedCall\""));
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
