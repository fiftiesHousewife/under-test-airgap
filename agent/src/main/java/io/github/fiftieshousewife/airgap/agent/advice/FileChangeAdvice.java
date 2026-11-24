package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.io.File;
import net.bytebuddy.asm.Advice;

/**
 * Copied into {@code File.delete} and {@code File.mkdir}. A refused change is skipped and answers {@code false}, as
 * the JDK does for a file it isn't allowed to change.
 */
public final class FileChangeAdvice {

    private FileChangeAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(@Advice.This final File file) {
        return FileGate.change(file);
    }

    @Advice.OnMethodExit
    @SuppressWarnings("PMD.AvoidReassigningParameters")
    @SuppressFBWarnings(value = {"IP_PARAMETER_IS_DEAD_BUT_OVERWRITTEN", "UC_USELESS_VOID_METHOD"},
            justification = "Byte Buddy turns the assignment into the method's return value")
    static void exit(@Advice.Enter final boolean refused, @Advice.Return(readOnly = false) boolean done) {
        if (refused) {
            done = false;
        }
    }
}
