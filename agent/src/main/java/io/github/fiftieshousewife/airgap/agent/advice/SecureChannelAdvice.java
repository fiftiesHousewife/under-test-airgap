package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.SecureStreamGate;
import java.nio.file.Path;
import java.util.Set;
import net.bytebuddy.asm.Advice;

/** Copied into the start of a secure directory stream's {@code newByteChannel}. */
public final class SecureChannelAdvice {

    private SecureChannelAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.This final Object stream, @Advice.Argument(0) final Path path,
            @Advice.Argument(1) final Set<?> options) {
        SecureStreamGate.channelInFolder(stream, path, options);
    }
}
