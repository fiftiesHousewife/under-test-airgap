package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code Runtime.exit}, which {@code System.exit} calls, and {@code Runtime.halt}. */
public final class ExitAdvice {

    private ExitAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Origin("#m") final String method, @Advice.Argument(0) final int status) {
        Gate.exit(method, status);
    }
}
