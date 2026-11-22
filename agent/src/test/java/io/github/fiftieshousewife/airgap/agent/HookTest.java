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
                        .contains("host-lookup-one", "tcp-socket", "tcp-channel", "udp-channel-send", "process"),
                () -> assertThat(Hook.values()).allSatisfy(hook -> assertAll(
                        () -> assertThat(hook.getTypeName()).as(hook.id())
                                .matches("(java\\.(net|lang)|sun\\.nio\\.ch)\\..+"),
                        () -> assertThat(hook.getMethods()).isNotNull(),
                        () -> assertThat(hook.getAdvice().getPackageName()).endsWith(".advice"))));
    }

    @Test
    void namesEachKindAsTheReportDoes() {
        assertThat(Arrays.stream(Kind.values()).map(Kind::getReportName))
                .containsExactly("host-lookup", "tcp", "udp", "process");
    }
}
