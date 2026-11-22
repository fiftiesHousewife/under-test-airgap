package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TargetTest {

    /** An address that isn't an internet address, like a Unix domain socket's. */
    private static final class OtherAddress extends SocketAddress {
        private static final long serialVersionUID = 1L;
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "app.localhost", "localhost.localdomain", "ip6-localhost",
        "127.0.0.1", "127.1.2.3", "::1", "[::1]", "0:0:0:0:0:0:0:1"})
    void namesTheMachineItself(final String host) {
        assertThat(Target.isLocalName(host)).isTrue();
    }

    @Test
    void treatsLookupsOfTheMachineAndOfWrittenAddressesAsLocal() {
        assertAll(
                () -> assertThat(Target.ofLookup("localhost").isLocal()).isTrue(),
                () -> assertThat(Target.ofLookup("192.0.2.1").isLocal()).isTrue(),
                () -> assertThat(Target.ofLookup("2001:db8::1").isLocal()).isTrue(),
                () -> assertThat(Target.ofLookup("db.example.invalid").isLocal()).isFalse(),
                () -> assertThat(Target.ofLookup("db.example.invalid").text()).isEqualTo("db.example.invalid"));
    }

    @Test
    void describesConnectionsWithoutLookingAnythingUp() throws java.net.UnknownHostException {
        final Target unresolved = Target.ofAddress(InetSocketAddress.createUnresolved("db.example.invalid", 5432));
        final Target loopback = Target.ofAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 80));
        final Target wildcard = Target.ofAddress(new InetSocketAddress(0));
        final Target documentation = Target.ofAddress(InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 53);

        assertAll(
                () -> assertThat(unresolved.isLocal()).isFalse(),
                () -> assertThat(unresolved.text()).isEqualTo("db.example.invalid:5432"),
                () -> assertThat(unresolved.getPort()).isEqualTo(5432),
                () -> assertThat(loopback.isLocal()).isTrue(),
                () -> assertThat(wildcard.isLocal()).isTrue(),
                () -> assertThat(documentation.isLocal()).isFalse(),
                () -> assertThat(documentation.text()).isEqualTo("192.0.2.1:53"));
    }

    @Test
    void treatsOtherKindsOfAddressAsLocal() {
        final SocketAddress unixSocket = new OtherAddress();

        assertAll(
                () -> assertThat(Target.ofAddress(unixSocket).isLocal()).isTrue(),
                () -> assertThat(Target.ofAddress(null, 53).isLocal()).isTrue());
    }

    @Test
    void namesAProgramByItsCommand() {
        assertAll(
                () -> assertThat(Target.ofCommand(List.of("/usr/bin/curl", "https://example.com")).text())
                        .isEqualTo("/usr/bin/curl"),
                () -> assertThat(Target.ofCommand(List.of()).getName()).isEmpty(),
                () -> assertThat(Target.ofCommand(List.of("curl")).isLocal()).isFalse());
    }
}
