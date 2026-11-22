package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllowlistTest {

    private final Allowlist allowlist = new Allowlist(List.of("WireMock.example.invalid:8089", "files.example.invalid",
            "process:git", "2001:db8::1"));

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
}
