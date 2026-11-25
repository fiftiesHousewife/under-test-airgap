package io.github.fiftieshousewife.airgap.agent;

/** Run in its own JVM by {@link SettingsTest}, in a project's folder: prints the mode its settings give. */
final class PrintsTheMode {

    private PrintsTheMode() {
    }

    /**
     * Prints the mode.
     *
     * @param arguments none
     */
    @SuppressWarnings("PMD.SystemPrintln")
    @SuppressForbidden("The test reads the mode from this program's standard output")
    public static void main(final String[] arguments) {
        System.out.print(Settings.parse(null).getMode());
    }
}
