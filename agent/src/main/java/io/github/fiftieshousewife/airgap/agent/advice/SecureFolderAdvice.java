package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.SecureStreamGate;
import java.nio.file.Path;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the end of the constructor of the JDK's secure directory stream (on Unix), so the paths its methods are
 * given, which are relative to its folder, can be told apart.
 */
public final class SecureFolderAdvice {

    private SecureFolderAdvice() {
    }

    @Advice.OnMethodExit
    static void exit(@Advice.This final Object stream, @Advice.Argument(0) final Path folder) {
        SecureStreamGate.openedFolder(stream, folder);
    }
}
