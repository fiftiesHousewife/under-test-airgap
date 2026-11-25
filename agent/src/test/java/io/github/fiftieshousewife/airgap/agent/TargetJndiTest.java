package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// A JNDI call's target: its URL, judged by its host and port, and which names reach other machines at all.
class TargetJndiTest {

    @Test
    void readsJndiPortsOnlyWhenTheyAreThere() {
        assertAll(
                () -> assertThat(Target.ofJndi("ldap://ldap.example.invalid:/o=x").getPort()).isEqualTo(-1),
                () -> assertThat(Target.ofJndi("ldap://ldap.example.invalid:123456/o=x").getPort()).isEqualTo(-1),
                () -> assertThat(Target.ofJndi("ldap://ldap.example.invalid:9/o=x").getPort()).isEqualTo(9),
                () -> assertThat(Target.ofJndi("ldap://ldap.example.invalid:65535").getPort()).isEqualTo(65535),
                () -> assertThat(Target.ofJndi("ldap://[2001:db8::1]/o=x").getPort()).isEqualTo(-1),
                () -> assertThat(Target.ofJndi("ldap://[2001:db8::1/o=x").getName()).as("no closing bracket")
                        .isEqualTo("2001:db8::1"),
                () -> assertThat(Target.ofJndi("ldap://a/o=x").getName()).isEqualTo("a"),
                () -> assertThat(Target.ofJndi("ldap://ldap.example.invalid?x").getName())
                        .isEqualTo("ldap.example.invalid"),
                () -> assertThat(Target.isNetworkJndiName(":ldap://x")).isFalse(),
                () -> assertThat(Target.isNetworkJndiName("l:x")).isFalse());
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
}
