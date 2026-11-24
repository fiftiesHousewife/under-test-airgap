package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.io.File;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code File.createNewFile}, which fails with {@code IOException} when refused. */
public final class FileCreateAdvice {

    private FileCreateAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.This final File file) {
        FileGate.create(file);
    }
}
