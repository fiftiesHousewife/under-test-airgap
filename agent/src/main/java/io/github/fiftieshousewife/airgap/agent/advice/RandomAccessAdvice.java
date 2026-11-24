package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code RandomAccessFile}'s private {@code open}, which all its constructors reach. */
public final class RandomAccessAdvice {

    private RandomAccessAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final String name, @Advice.Argument(1) final int mode) {
        FileGate.randomAccess(name, mode);
    }
}
