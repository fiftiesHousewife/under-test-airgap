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

    // An address that isn't an internet address, like a Unix domain socket's.
    private static final class OtherAddress extends SocketAddress {
        private static final long serialVersionUID = 1L;
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "app.localhost", "localhost.localdomain", "ip6-localhost",
        "127.0.0.1", "127.1.2.3", "::1", "[::1]", "0:0:0:0:0:0:0:1"})
    void namesTheMachineItself(final String host) {
        assertThat(Target.isLocalName(host)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.example.invalid", "127.0.0.1.example.invalid", "127.0.0", "127.0.0.256",
        "localhost.example.invalid"})
    void looksUpNamesThatOnlyStartLikeTheMachineItself(final String host) {
        assertThat(Target.isLocalName(host)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"999.0.2.1", "192.0.2.256", "192.0.2", "192.0.2.1.5", "192..2.1"})
    void looksUpNamesThatOnlyLookLikeIpv4Addresses(final String host) {
        assertThat(Target.ofLookup(host).isLocal()).isFalse();
    }

    @Test
    void treatsLookupsOfTheMachineAndOfWrittenAddressesAsLocal() {
        assertAll(
                () -> assertThat(Target.ofLookup("localhost").isLocal()).isTrue(),
                () -> assertThat(Target.ofLookup("192.0.2.1").isLocal()).isTrue(),
                () -> assertThat(Target.ofLookup("255.255.255.255").isLocal()).isTrue(),
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
    void treatsTheMachinesOwnAddressesAsLocalButNotTheirNames() throws java.net.SocketException {
        final InetAddress own = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()).stream()
                .flatMap(network -> java.util.Collections.list(network.getInetAddresses()).stream())
                .filter(address -> !address.isLoopbackAddress()).findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(own != null, "this machine has no network address but loopback");

        assertAll(
                () -> assertThat(Target.ofAddress(new InetSocketAddress(own, 1099)).isLocal()).isTrue(),
                () -> assertThat(Target.ofAddress(own, 1099).isLocal()).isTrue(),
                () -> assertThat(Target.isThisMachines(own)).isTrue(),
                () -> assertThat(Target.ofReverseLookup(own).isLocal()).as("its name comes from DNS").isFalse(),
                () -> assertThat(Target.isThisMachines(InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1})))
                        .isFalse());
    }

    @Test
    void treatsOtherKindsOfAddressAsLocal() {
        final SocketAddress unixSocket = new OtherAddress();

        assertAll(
                () -> assertThat(Target.ofAddress(unixSocket).isLocal()).isTrue(),
                () -> assertThat(Target.ofAddress(null, 53).isLocal()).isTrue());
    }

    @Test
    void namesAReverseLookupByItsAddress() throws java.net.UnknownHostException {
        final Target remote = Target.ofReverseLookup(InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 9}));
        assertAll(
                () -> assertThat(remote.text()).isEqualTo("192.0.2.9"),
                () -> assertThat(remote.isLocal()).isFalse(),
                () -> assertThat(Target.ofReverseLookup(InetAddress.getLoopbackAddress()).isLocal()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1a.0.2.1", "192.0.2.x", "db:example", "fe80::1g"})
    void looksUpNamesWithCharactersNoAddressHas(final String host) {
        assertThat(Target.ofLookup(host).isLocal()).isFalse();
    }

    @Test
    void namesAProgramByItsCommand() {
        assertAll(
                () -> assertThat(Target.ofCommand(List.of("/usr/bin/curl", "https://example.com")).text())
                        .isEqualTo("/usr/bin/curl"),
                () -> assertThat(Target.ofCommand(List.of()).getName()).isEmpty(),
                () -> assertThat(Target.ofCommand(List.of("curl")).isLocal()).isFalse());
    }

    @Test
    void namesLibrariesAndExits() {
        assertAll(
                () -> assertThat(Target.ofLibrary("/opt/lib/libnative.so").text()).isEqualTo("/opt/lib/libnative.so"),
                () -> assertThat(Target.ofLibrary("netty_transport_native_epoll").isLocal()).isFalse(),
                () -> assertThat(Target.ofExit("halt", 3).text()).isEqualTo("halt(3)"),
                () -> assertThat(Target.ofExit("exit", 0).isLocal()).isFalse());
    }
}
