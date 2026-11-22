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

    @Test
    void namesAJndiCallByItsUrlAndJudgesItByItsHost() {
        final Target ldap = Target.ofJndi("ldap://ldap.example.invalid:389/dc=example,dc=com");
        final Target rmi = Target.ofJndi("rmi://[2001:db8::1]:1099/orders");
        final Target dns = Target.ofJndi("dns:/example.com");

        assertAll(
                () -> assertThat(ldap.text()).isEqualTo("ldap://ldap.example.invalid:389/dc=example,dc=com"),
                () -> assertThat(ldap.getName()).isEqualTo("ldap.example.invalid"),
                () -> assertThat(ldap.getPort()).isEqualTo(389),
                () -> assertThat(ldap.isLocal()).isFalse(),
                () -> assertThat(rmi.getName()).isEqualTo("2001:db8::1"),
                () -> assertThat(rmi.getPort()).isEqualTo(1099),
                () -> assertThat(Target.ofJndi("LDAP://user@ldap.example.invalid/o=x").getName())
                        .isEqualTo("ldap.example.invalid"),
                () -> assertThat(Target.ofJndi("ldap://localhost:389/o=x").isLocal()).isTrue(),
                () -> assertThat(Target.ofJndi("ldap:///o=x").isLocal()).as("no host means the machine itself")
                        .isTrue(),
                () -> assertThat(dns.isLocal()).as("no host means the system's DNS servers").isFalse(),
                () -> assertThat(dns.getPort()).isEqualTo(-1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ldap://h/o", "LDAPS://h/o", "rmi://h/x", "dns://h/x", "dns:x", "iiop://h/x",
        "iiopname://h/x", "corbaname:iiop:h#x"})
    void recognisesJndiNamesThatReachOtherMachines(final String name) {
        assertThat(Target.isNetworkJndiName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"java:comp/env/jdbc/orders", "jdbc/orders", "", "ldap", "file:/tmp/x", "x:ldap://h"})
    void leavesOtherJndiNamesAlone(final String name) {
        assertThat(Target.isNetworkJndiName(name)).isFalse();
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
