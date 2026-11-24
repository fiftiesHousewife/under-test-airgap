package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code FileOutputStream}'s private {@code open}, which all its constructors reach. */
public final class FileStreamAdvice {

    private FileStreamAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final String name) {
        FileGate.open(name);
    }
}
