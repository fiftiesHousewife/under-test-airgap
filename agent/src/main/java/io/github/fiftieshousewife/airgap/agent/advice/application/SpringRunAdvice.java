package io.github.fiftieshousewife.airgap.agent.advice.application;

import io.github.fiftieshousewife.airgap.agent.SpringGate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into Spring Boot's {@code SpringApplication.run}: calls made until it returns, such as fetching configuration
 * from Spring Cloud Config or Vault, are made while the application is starting.
 */
public final class SpringRunAdvice {

    private SpringRunAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter() {
        SpringGate.applicationStarting();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void exit() {
        SpringGate.startUpEnded();
    }
}
