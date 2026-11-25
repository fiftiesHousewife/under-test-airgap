package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of JUnit 4's {@code RunNotifier.fireTestStarted}, before it tells any listener: the test
 * starts on this thread. JUnit's {@code Description} is read only as text, so the agent needs no JUnit.
 */
public final class JUnit4StartAdvice {

    private JUnit4StartAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final Object description) {
        Gate.enterJUnit4Test(String.valueOf(description));
    }
}
