package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.nio.file.Path;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of the default file system's {@code createDirectory}, its delete (which {@code Files.delete}
 * and {@code deleteIfExists} reach), {@code createSymbolicLink} and {@code createLink}: each changes the path it is
 * given first.
 */
public final class PathAdvice {

    private PathAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final Path path) {
        FileGate.path(path);
    }
}
