package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.NativeGate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of {@code SymbolLookup.libraryLookup}: before JDK's check for native access, so a refusal
 * doesn't set off its warning.
 */
public final class ForeignLibraryAdvice {

    private ForeignLibraryAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final Object library) {
        NativeGate.foreignLibrary(String.valueOf(library));
    }
}
