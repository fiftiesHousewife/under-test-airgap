package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.io.File;
import net.bytebuddy.asm.Advice;

/**
 * Copied into {@code File.renameTo}, which changes both the file and where it goes. A refused rename is skipped and
 * answers {@code false}, as the JDK does for one it isn't allowed to make.
 */
public final class FileRenameAdvice {

    private FileRenameAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(@Advice.This final File file, @Advice.Argument(0) final File destination) {
        return FileGate.change(file) || FileGate.change(destination);
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
