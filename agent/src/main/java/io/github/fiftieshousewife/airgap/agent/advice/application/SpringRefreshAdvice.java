package io.github.fiftieshousewife.airgap.agent.advice.application;

import io.github.fiftieshousewife.airgap.agent.SpringGate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into Spring's {@code AbstractApplicationContext.refresh}, which starts every Spring context: it fails at once
 * if a production profile is active, and calls made until it returns are made while the context is starting.
 */
public final class SpringRefreshAdvice {

    private SpringRefreshAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.This final Object context) {
        SpringGate.contextRefreshing(context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void exit() {
        SpringGate.startUpEnded();
    }
}
