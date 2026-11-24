package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.nio.file.Path;
import net.bytebuddy.asm.Advice;

/** Copied into the start of the default file system's {@code move}, which changes both the file and where it goes. */
public final class MoveAdvice {

    private MoveAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final Path source, @Advice.Argument(1) final Path target) {
        FileGate.path(source);
        FileGate.path(target);
    }
}
