package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of {@code System.loadLibrary}, {@code System.load} and their {@code Runtime} versions: before
 * JDK 24's check for native access, so the self-check's probe doesn't set off its warning.
 */
public final class NativeLibraryAdvice {

    private NativeLibraryAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final String library) {
        Gate.nativeLibrary(library);
    }
}
