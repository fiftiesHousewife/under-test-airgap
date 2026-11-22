package io.github.fiftieshousewife.airgap.agent;

/**
 * Compares setting values and host names without regard to case, for the letters A to Z only. Host names and the
 * agent's settings are plain ASCII, and folding case only there avoids the surprises of Unicode case rules.
 */
final class Ascii {

    private static final int CASE_DISTANCE = 'a' - 'A';

    private Ascii() {
    }

    static boolean equalsIgnoringCase(final String one, final String other) {
        if (one.length() != other.length()) {
            return false;
        }
        for (int i = 0; i < one.length(); i++) {
            if (lower(one.charAt(i)) != lower(other.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean endsWithIgnoringCase(final String text, final String suffix) {
        return text.length() >= suffix.length()
                && equalsIgnoringCase(text.substring(text.length() - suffix.length()), suffix);
    }

    private static char lower(final char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + CASE_DISTANCE) : c;
    }
}
