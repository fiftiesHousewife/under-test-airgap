package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HookTest {

    @Test
    void eachHookHasAnIdAClassMethodsAndAdvice() {
        assertAll(
                () -> assertThat(Arrays.stream(Hook.values()).map(Hook::id)).doesNotHaveDuplicates()
                        .contains("host-lookup-all", "tcp-socket", "tcp-channel", "udp-channel-send", "process",
                                "jndi-name", "jndi-provider", "native-library-by-name", "exit", "halt"),
                () -> assertThat(Hook.values()).allSatisfy(hook -> assertAll(
                        () -> assertThat(hook.getTypeName()).as(hook.id())
                                .matches("(java\\.(net|lang)|javax\\.naming|sun\\.nio\\.ch"
                                        + "|jdk\\.internal\\.net\\.http)\\..+"),
                        () -> assertThat(hook.getMethods()).isNotNull(),
                        () -> assertThat(hook.getAdvice().getPackageName()).endsWith(".advice"))));
    }

    @Test
    void knowsTheKindOfCallEachHookCatches() {
        assertAll(
                () -> assertThat(Hook.HOST_LOOKUP_ALL.kind()).isEqualTo(Kind.HOST_LOOKUP),
                () -> assertThat(Hook.TCP_ASYNC.kind()).isEqualTo(Kind.TCP),
                () -> assertThat(Hook.UDP_CHANNEL_SEND.kind()).isEqualTo(Kind.UDP),
                () -> assertThat(Hook.PROCESS.kind()).isEqualTo(Kind.PROCESS),
                () -> assertThat(Hook.JNDI_PROVIDER.kind()).isEqualTo(Kind.JNDI),
                () -> assertThat(Hook.NATIVE_LIBRARY_BY_PATH.kind()).isEqualTo(Kind.NATIVE_LIBRARY),
                () -> assertThat(Hook.EXIT.kind()).isEqualTo(Kind.EXIT),
                () -> assertThat(Hook.HALT.kind()).isEqualTo(Kind.EXIT));
    }

    @Test
    void namesEachKindAsTheReportDoes() {
        assertThat(Arrays.stream(Kind.values()).map(Kind::getReportName))
                .containsExactly("host-lookup", "tcp", "udp", "process", "jndi", "native-library", "exit");
    }

    @Test
    void knowsWhichHooksApplyOnThisJdk() {
        assertAll(
                () -> assertThat(Hook.HOST_LOOKUP_ALL.appliesHere()).isTrue(),
                () -> assertThat(Hook.TCP_SOCKET_ADAPTOR.appliesHere()).as("from JDK 13").isTrue(),
                () -> assertThat(Hook.NATIVE_LIBRARY_FOREIGN_BY_NAME.appliesHere()).as("from JDK 22").isTrue(),
                () -> assertThat(Hook.TCP_HTTP_CLIENT_SEND.appliesHere()).as("from JDK 11").isTrue(),
                () -> assertThat(Hook.TCP_HTTP_CLIENT_TASKS.appliesHere()).isTrue(),
                () -> assertThat(Hook.TCP_HTTP_CLIENT_SEND.refuses()).isFalse(),
                () -> assertThat(Hook.TCP_HTTP_CLIENT_TASKS.refuses()).isFalse(),
                () -> assertThat(Hook.TCP_SOCKET.refuses()).isTrue(),
                () -> assertThat(Hook.load("io.github.fiftieshousewife.airgap.agent.NoSuchClass")).isNull(),
                () -> assertThat(Hook.load("java.net.InetAddress")).isEqualTo(java.net.InetAddress.class));
    }

    /** The lookup hook is on the one private getAllByName0 every lookup reaches: the one with the most parameters. */
    @Test
    void hooksTheJdksInnermostLookup() {
        final net.bytebuddy.description.method.MethodList<?> hooked =
                net.bytebuddy.description.type.TypeDescription.ForLoadedType.of(java.net.InetAddress.class)
                        .getDeclaredMethods().filter(Hook.HOST_LOOKUP_ALL.getMethods());

        assertThat(hooked).singleElement().satisfies(method -> assertAll(
                () -> assertThat(method.getName()).isEqualTo("getAllByName0"),
                () -> assertThat(method.isPrivate()).isTrue(),
                () -> assertThat(method.getParameters().size()).isGreaterThan(1)));
    }
}
