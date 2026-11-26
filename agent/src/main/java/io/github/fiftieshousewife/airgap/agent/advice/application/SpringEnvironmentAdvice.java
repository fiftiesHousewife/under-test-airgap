package io.github.fiftieshousewife.airgap.agent.advice.application;

import io.github.fiftieshousewife.airgap.agent.SpringGate;
import net.bytebuddy.asm.Advice;

/** Copied into the end of the constructor of Spring's {@code AbstractEnvironment}: an environment has been made. */
public final class SpringEnvironmentAdvice {

    private SpringEnvironmentAdvice() {
    }

    @Advice.OnMethodExit
    static void exit(@Advice.This final Object environment) {
        SpringGate.environmentMade(environment);
    }
}
