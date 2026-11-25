package io.github.fiftieshousewife.airgap.agent;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

// Run in its own JVM by SettingsTest, in a project's folder: prints the mode its settings give.
final class PrintsTheMode {

    private PrintsTheMode() {
    }

    // Prints the mode.
    //
    // arguments: none
    public static void main(final String[] arguments) throws UnsupportedEncodingException {
        // Standard output by its file descriptor, so the test can read the mode; the agent sees no file opened.
        try (PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8")) {
            out.print(Settings.parse(null).getMode());
        }
    }
}
