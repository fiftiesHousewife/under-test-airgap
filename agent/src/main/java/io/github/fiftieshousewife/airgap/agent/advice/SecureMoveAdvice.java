package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of a secure directory stream's {@code move(from, toStream, to)}, from its folder to another
 * stream's.
 */
public final class SecureMoveAdvice {

    private SecureMoveAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.This final Object stream, @Advice.AllArguments final Object[] arguments) {
        FileGate.secureMove(stream, arguments);
    }
}
