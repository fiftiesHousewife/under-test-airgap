package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

/** Lines as strace writes them, on Linux. */
class OsTraceTest {

    @Test
    void readsConnectionsAndPacketsToOtherMachines() {
        assertAll(
                () -> assertThat(OsTrace.parse("connect(12, {sa_family=AF_INET, sin_port=htons(5432), "
                        + "sin_addr=inet_addr(\"192.0.2.1\")}, 16) = -1 EINPROGRESS (Operation now in progress)"))
                        .hasToString("network 192.0.2.1:5432"),
                () -> assertThat(OsTrace.parse("sendto(7, \"\\x12\\x34\", 40, MSG_NOSIGNAL, {sa_family=AF_INET6, "
                        + "sin6_port=htons(53), sin6_flowinfo=htonl(0), inet_pton(AF_INET6, \"2001:db8::1\", "
                        + "&sin6_addr), sin6_scope_id=0}, 28) = 40")).hasToString("network 2001:db8::1:53"),
                () -> assertThat(OsTrace.parse("sendmsg(9, {msg_name={sa_family=AF_INET, sin_port=htons(123), "
                        + "sin_addr=inet_addr(\"192.0.2.9\")}, msg_namelen=16}, 0) = 48"))
                        .hasToString("network 192.0.2.9:123"));
    }

    @Test
    void leavesTheMachineItselfAlone() {
        assertAll(
                () -> assertThat(OsTrace.parse("connect(5, {sa_family=AF_INET, sin_port=htons(40213), "
                        + "sin_addr=inet_addr(\"127.0.0.1\")}, 16) = 0")).isNull(),
                () -> assertThat(OsTrace.parse("connect(5, {sa_family=AF_INET6, sin6_port=htons(80), "
                        + "sin6_flowinfo=htonl(0), inet_pton(AF_INET6, \"::1\", &sin6_addr), sin6_scope_id=0}, 28)"
                        + " = 0"))
                        .isNull(),
                () -> assertThat(OsTrace.parse("connect(5, {sa_family=AF_UNIX, sun_path=\"/run/nscd/socket\"}, 110)"
                        + " = -1 ENOENT (No such file or directory)")).isNull());
    }

    @Test
    void readsProgramsStartedOtherThanJava() {
        assertAll(
                () -> assertThat(OsTrace.parse("execve(\"/usr/bin/curl\", [\"curl\", \"https://example.com\"], "
                        + "0x7ffd /* 20 vars */) = 0")).hasToString("program /usr/bin/curl"),
                () -> assertThat(OsTrace.parse("execve(\"" + System.getProperty("java.home")
                        + "/bin/java\", [\"java\"], 0x7ffd) = 0")).as("the JDK's own").isNull(),
                () -> assertThat(OsTrace.parse("execve(\"/usr/bin/curl\", [\"curl\"], 0x7ffd) = -1 ENOENT "
                        + "(No such file or directory)")).as("not started").isNull());
    }

    @Test
    void readsLibrariesOpenedFromOutsideTheSystemAndTheJdk() {
        assertAll(
                () -> assertThat(OsTrace.parse("openat(AT_FDCWD, \"/tmp/sqlite-3.45-libsqlitejdbc.so\", "
                        + "O_RDONLY|O_CLOEXEC) = 3")).hasToString("library /tmp/sqlite-3.45-libsqlitejdbc.so"),
                () -> assertThat(OsTrace.parse("openat(AT_FDCWD, \"/lib/x86_64-linux-gnu/libc.so.6\", "
                        + "O_RDONLY|O_CLOEXEC) = 3")).isNull(),
                () -> assertThat(OsTrace.parse("openat(AT_FDCWD, \"/tmp/libmissing.so\", O_RDONLY|O_CLOEXEC) = -1 "
                        + "ENOENT (No such file or directory)")).as("not opened").isNull(),
                () -> assertThat(OsTrace.parse("openat(AT_FDCWD, \"/tmp/libvendor.so.1.2\", O_RDONLY|O_CLOEXEC) = 3"))
                        .as("a library with a version").hasToString("library /tmp/libvendor.so.1.2"),
                () -> assertThat(OsTrace.parse("openat(AT_FDCWD, \"/etc/ld.so.cache\", O_RDONLY|O_CLOEXEC) = 3"))
                        .as("the dynamic linker's index, not a library").isNull(),
                () -> assertThat(OsTrace.parse("openat(AT_FDCWD, \"/tmp/notes.txt\", O_RDONLY) = 3")).isNull());
    }
}
