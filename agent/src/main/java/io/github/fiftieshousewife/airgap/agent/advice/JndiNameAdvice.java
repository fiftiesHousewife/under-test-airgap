package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.NetworkGate;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code InitialContext.getURLOrDefaultInitCtx(String)}, which every lookup reaches. */
public final class JndiNameAdvice {

    private JndiNameAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final String name) {
        NetworkGate.jndi(name);
    }
}
