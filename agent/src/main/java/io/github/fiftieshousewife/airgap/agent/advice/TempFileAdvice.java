package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.FileGate;
import java.io.File;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of {@code File.createTempFile(prefix, suffix, directory)}, which the other
 * {@code createTempFile} reaches and which creates its file without {@code createNewFile}. With no directory, the file
 * goes in the temporary folder, which is the project's.
 */
public final class TempFileAdvice {

    private TempFileAdvice() {
    }

    @Advice.OnMethodEnter
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "Names the file the code under test is creating, to check it; opens nothing")
    static void enter(@Advice.Argument(0) final String prefix, @Advice.Argument(2) final File directory) {
        if (directory != null) {
            FileGate.create(new File(directory, prefix));
        }
    }
}
