package io.github.fiftieshousewife.airgap.maven;

// What a Surefire or Failsafe version can do. From 2.17 they replace @{property} in argLine as tests start, after
// JaCoCo has set its argLine; older ones replace it when Maven reads the pom.
final class SurefireVersions {

    private static final int FIRST_LATE_MAJOR = 2;
    private static final int FIRST_LATE_MINOR = 17;
    private static final int[] UNKNOWN = {0, 0};

    private SurefireVersions() {
    }

    // version: the plugin's version, such as 2.12.4 or 3.0.0-M5; null if not known
    // Returns true from 2.17
    static boolean replacesLate(final String version) {
        final int[] number = majorAndMinor(version);
        return number[0] > FIRST_LATE_MAJOR || number[0] == FIRST_LATE_MAJOR && number[1] >= FIRST_LATE_MINOR;
    }

    private static int[] majorAndMinor(final String version) {
        if (version == null) {
            return UNKNOWN;
        }
        final String[] parts = version.split("[.-]");
        try {
            return new int[] {Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0};
        } catch (NumberFormatException unknown) {
            return UNKNOWN;
        }
    }
}
