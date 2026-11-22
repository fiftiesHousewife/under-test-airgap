package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.SctpGate;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code SctpMultiChannel.send}, whose message names the address. */
public final class SctpSendAdvice {

    private SctpSendAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(1) final Object messageInfo) {
        SctpGate.sctpSend(messageInfo);
    }
}
