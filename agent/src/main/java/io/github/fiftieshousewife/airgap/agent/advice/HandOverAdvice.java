package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.HandOver;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the JDK HTTP client's executor, which runs every task of every request: each task carries the test,
 * allowances and caller of the thread that handed it over.
 */
public final class HandOverAdvice {

    private HandOverAdvice() {
    }

    @Advice.OnMethodEnter
    @SuppressWarnings("PMD.AvoidReassigningParameters")
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
            justification = "Byte Buddy turns the assignment into the method's argument")
    static void enter(@Advice.Argument(value = 0, readOnly = false) Runnable task) {
        task = HandOver.carry(task);
    }
}
