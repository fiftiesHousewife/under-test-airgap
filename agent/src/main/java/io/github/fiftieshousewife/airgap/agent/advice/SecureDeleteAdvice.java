package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.SecureStreamGate;
import java.nio.file.Path;
import net.bytebuddy.asm.Advice;

/** Copied into the start of a secure directory stream's {@code deleteFile} and {@code deleteDirectory}. */
public final class SecureDeleteAdvice {

    private SecureDeleteAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.This final Object stream, @Advice.Argument(0) final Path path) {
        SecureStreamGate.inFolder(stream, path);
    }
}
