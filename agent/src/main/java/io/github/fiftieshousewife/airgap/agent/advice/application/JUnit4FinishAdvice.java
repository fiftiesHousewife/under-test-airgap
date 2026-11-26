package io.github.fiftieshousewife.airgap.agent.advice.application;

import io.github.fiftieshousewife.airgap.agent.Gate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the end of JUnit 4's {@code RunNotifier.fireTestFinished}, after it has told every listener: the test
 * has ended on this thread.
 */
public final class JUnit4FinishAdvice {

    private JUnit4FinishAdvice() {
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void exit() {
        Gate.leaveTest();
    }
}
