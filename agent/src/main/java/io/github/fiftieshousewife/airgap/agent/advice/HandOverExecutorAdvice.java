package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.HandOver;
import java.util.concurrent.Executor;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the JDK HTTP client's private {@code sendAsync}, which every request reaches: the executor it is given
 * runs each of the request's tasks with the sender's test, allowances and caller.
 */
public final class HandOverExecutorAdvice {

    private HandOverExecutorAdvice() {
    }

    @Advice.OnMethodEnter
    @SuppressWarnings("PMD.AvoidReassigningParameters")
    @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE",
            justification = "Byte Buddy turns the assignment into the method's argument")
    static void enter(@Advice.Argument(value = 3, readOnly = false) Executor executor) {
        executor = HandOver.carryingTo(executor);
    }
}
