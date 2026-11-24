package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.nio.file.Path;
import java.util.Set;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of the default file system's {@code newByteChannel}, {@code newFileChannel} and
 * {@code newAsynchronousFileChannel}, which every {@code Files} stream, writer and channel reaches. Only a file opened
 * to write, append or delete on close is checked.
 */
public final class ChannelAdvice {

    private ChannelAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final Path path, @Advice.Argument(1) final Set<?> options) {
        FileGate.channel(path, options);
    }
}
