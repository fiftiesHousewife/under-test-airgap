package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code InetAddress}'s lookups. */
public final class LookupAdvice {

    private LookupAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final String host) {
        Gate.lookup(host);
    }
}
