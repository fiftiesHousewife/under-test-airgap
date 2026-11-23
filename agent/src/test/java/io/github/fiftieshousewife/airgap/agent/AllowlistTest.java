package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllowlistTest {

    private final Allowlist allowlist = new Allowlist(List.of("WireMock.example.invalid:8089", "files.example.invalid",
            "process:git", "2001:db8::1", "native:SQLiteJDBC", "ldap.example.invalid:636"));

    private static Target remote(final String host, final int port) {
        return Target.ofAddress(InetSocketAddress.createUnresolved(host, port));
    }

    @Test
    void allowsAHostOnItsPortOnlyWhenAPortIsGiven() {
        assertAll(
                () -> assertThat(allowlist.allows(Kind.TCP, remote("wiremock.example.invalid", 8089))).isTrue(),
                () -> assertThat(allowlist.allows(Kind.TCP, remote("wiremock.example.invalid", 8090))).isFalse(),
                () -> assertThat(allowlist.allows(Kind.HOST_LOOKUP, Target.ofLookup("wiremock.example.invalid")))
                        .isTrue(),
                () -> assertThat(allowlist.allows(Kind.TCP, remote("files.example.invalid", 21))).isTrue(),
                () -> assertThat(allowlist.allows(Kind.TCP, remote("2001:db8::1", 443))).isTrue(),
                () -> assertThat(allowlist.allows(Kind.TCP, remote("db.example.invalid", 5432))).isFalse());
    }

    @Test
    void allowsProgramsOnlyByProcessEntries() {
        assertAll(
                () -> assertThat(allowlist.allows(Kind.PROCESS, Target.ofCommand(List.of("/usr/bin/git")))).isTrue(),
                () -> assertThat(allowlist.allows(Kind.PROCESS, Target.ofCommand(List.of("C:\\Git\\git.exe"))))
                        .isTrue(),
                () -> assertThat(allowlist.allows(Kind.PROCESS, Target.ofCommand(List.of("curl")))).isFalse(),
                () -> assertThat(allowlist.allows(Kind.PROCESS, Target.ofCommand(List.of("files.example.invalid"))))
                        .isFalse(),
                () -> assertThat(allowlist.allows(Kind.TCP, remote("git", 22))).isFalse());
    }

    @Test
    void allowsNativeLibrariesOnlyByNativeEntries() {
        assertAll(
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary("sqlitejdbc"))).isTrue(),
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary("/tmp/x/libsqlitejdbc.so")))
                        .isTrue(),
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY,
                        Target.ofLibrary("C:\\Temp\\sqlitejdbc.dll"))).isTrue(),
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary("libsqlitejdbc.dylib")))
                        .isTrue(),
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY,
                        Target.ofLibrary("netty_transport_native_epoll"))).isFalse(),
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary("git"))).isFalse(),
                () -> assertThat(allowlist.allows(Kind.TCP, remote("native", 0))).isFalse());
    }

    @Test
    void allowsJndiByHostAndNeverAllowsAnExit() {
        assertAll(
                () -> assertThat(allowlist.allows(Kind.JNDI, Target.ofJndi("ldaps://ldap.example.invalid:636/o=x")))
                        .isTrue(),
                () -> assertThat(allowlist.allows(Kind.JNDI, Target.ofJndi("ldap://ldap.example.invalid:389/o=x")))
                        .isFalse(),
                () -> assertThat(allowlist.allows(Kind.JNDI, Target.ofJndi("ldap://files.example.invalid/o=x")))
                        .isTrue(),
                () -> assertThat(new Allowlist(List.of("exit(1)", "exit")).allows(Kind.EXIT, Target.ofExit("exit", 1)))
                        .isFalse());
    }

    /** The names sqlite-jdbc and Netty give the libraries they extract, with versions and random ids in them. */
    @Test
    void matchesNativeNamesExactlyOrByWildcards() {
        final Allowlist wildcards = new Allowlist(List.of("native:*sqlitejdbc", "native:netty_transport_native_epoll*",
                "native:jni*dispatch"));
        assertAll(
                () -> assertThat(wildcards.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary(
                        "/tmp/sqlite-3.45.1.0-1f0c6d2e-8b1a-4c1e-9d55-2b7f6c3e9a10-libsqlitejdbc.so"))).isTrue(),
                () -> assertThat(wildcards.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary(
                        "C:\\Temp\\sqlite-3.45.1.0-1f0c6d2e-sqlitejdbc.dll"))).isTrue(),
                () -> assertThat(wildcards.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary(
                        "/tmp/libnetty_transport_native_epoll_x86_644839027761524470398.so"))).isTrue(),
                () -> assertThat(wildcards.allows(Kind.NATIVE_LIBRARY,
                        Target.ofLibrary("/tmp/jna1234/libjnidispatch.so.4"))).isTrue(),
                () -> assertThat(wildcards.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary("/tmp/libsqlitejdbc-extra.so")))
                        .as("a wildcard only where the entry has one").isFalse(),
                () -> assertThat(wildcards.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary("/tmp/libnetty_tcnative.so")))
                        .isFalse(),
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY,
                        Target.ofLibrary("/tmp/sqlite-3.45.1.0-1f0c6d2e-libsqlitejdbc.so")))
                        .as("no wildcard, no match").isFalse(),
                () -> assertThat(allowlist.allows(Kind.NATIVE_LIBRARY, Target.ofLibrary("/usr/lib/libsqlitejdbc.so.1")))
                        .as("a versioned .so keeps its name").isTrue());
    }
}
