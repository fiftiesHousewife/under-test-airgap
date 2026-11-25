package io.github.fiftieshousewife.airgap.agent.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// The checks on a settings file's values: that each is text, one of a few words, or a list, and that each entry of a
// list such as allow names one thing and gives the reason for it. Each failure says what is wrong in the file's own
// words.
final class SettingsEntries {

    private static final String FOR_EXAMPLE = "for example";

    private final String name;
    private final String key;
    private final String example;

    // name: what an entry is called in messages, such as "allow entry"
    // key: the key that names the entry's one thing, such as target
    // example: an entry written out, for messages
    SettingsEntries(final String name, final String key, final String example) {
        this.name = name;
        this.key = key;
        this.example = example;
    }

    String getName() {
        return name;
    }

    // Returns the thing the entry names, after checking it has only that and a reason
    String entry(final int number, final Object entry) {
        final String numbered = name + " " + number;
        if (!(entry instanceof Map)) {
            throw new IllegalArgumentException(numbered + " (" + shown(entry) + ") needs a " + key
                    + " and a reason, " + example.substring(example.indexOf(FOR_EXAMPLE)));
        }
        final Map<?, ?> keys = (Map<?, ?>) entry;
        onlyEntryKeys(numbered, keys);
        final String named = text(numbered + "'s " + key, keys.get(key));
        if (isEmpty(keys.get("reason"))) {
            throw new IllegalArgumentException(numbered + " (" + named + ") needs a reason: " + example);
        }
        return named;
    }

    private void onlyEntryKeys(final String numbered, final Map<?, ?> keys) {
        for (final Object given : keys.keySet()) {
            if (!key.equals(given) && !"reason".equals(given)) {
                throw new IllegalArgumentException(numbered + " has '" + given + "'; an entry has only "
                        + Arrays.asList(key, "reason"));
            }
        }
    }

    static String oneOf(final String key, final Object value, final String... allowed) {
        if (!(value instanceof String) || !Arrays.asList(allowed).contains(value)) {
            throw new IllegalArgumentException(key + " is " + shown(value) + "; it must be one of "
                    + Arrays.asList(allowed));
        }
        return (String) value;
    }

    static List<?> list(final String key, final Object value, final String what) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(key + " must be a list of " + what + ", not " + shown(value));
        }
        return (List<?>) value;
    }

    static String noSpaces(final String what, final String text) {
        if (!text.equals(text.replaceAll("\\s", ""))) {
            throw new IllegalArgumentException(what + " '" + text + "' has a space in it");
        }
        return text;
    }

    static String text(final String what, final Object value) {
        if (isEmpty(value)) {
            throw new IllegalArgumentException(what + " must be text, not " + shown(value));
        }
        return (String) value;
    }

    private static boolean isEmpty(final Object value) {
        return !(value instanceof String) || ((String) value).isEmpty();
    }

    private static String shown(final Object value) {
        if (value == null) {
            return "empty";
        }
        return value instanceof String ? "'" + value + "'" : value.toString();
    }
}
