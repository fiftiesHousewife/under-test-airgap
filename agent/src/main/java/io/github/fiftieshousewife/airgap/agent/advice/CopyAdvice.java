package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.nio.file.Path;
import net.bytebuddy.asm.Advice;

/** Copied into the start of the default file system's {@code copy}, which writes only where it copies to. */
public final class CopyAdvice {

    private CopyAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(1) final Path target) {
        FileGate.path(target);
    }
}
