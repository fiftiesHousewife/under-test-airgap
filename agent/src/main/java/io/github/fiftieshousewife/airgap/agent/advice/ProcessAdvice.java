package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code ProcessBuilder.start}. */
public final class ProcessAdvice {

    private ProcessAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.This final ProcessBuilder builder) {
        Gate.process(builder.command());
    }
}
