package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A project's settings file, {@code testing/airgap.yaml}, checked by the rules of
 * {@code schema/airgap-settings.schema.json}: its {@code mode}, and the stand-ins its {@code allow} list names, each
 * with the reason it's allowed. {@code production_profiles} and {@code file_writes} are checked, and kept for the
 * hooks that will use them.
 */
final class SettingsFile {

    private static final List<String> KEYS = Arrays.asList("mode", "allow", "production_profiles", "file_writes");
    private static final List<String> ENTRY_KEYS = Arrays.asList("target", "reason");
    private static final String EXAMPLE = "for example '- target: localhost:8089' with"
            + " 'reason: WireMock standing in for the postcode service' below it";

    private Mode mode;
    private final List<String> allow = new ArrayList<>();

    private SettingsFile() {
    }

    /**
     * Reads and checks a settings file's text.
     *
     * @param text the file's text
     * @return its settings
     * @throws IllegalArgumentException saying what is wrong, and on which line where the YAML itself is wrong
     */
    static SettingsFile read(final String text) {
        final SettingsFile file = new SettingsFile();
        for (final Map.Entry<String, Object> key : Yaml.read(text).entrySet()) {
            file.set(key.getKey(), key.getValue());
        }
        return file;
    }

    private void set(final String key, final Object value) {
        if ("mode".equals(key)) {
            mode = modeOf(value);
        } else if ("allow".equals(key)) {
            for (final Object entry : list(key, value, "stand-ins, each with a target and a reason")) {
                allow.add(target(allow.size() + 1, entry));
            }
        } else if ("production_profiles".equals(key)) {
            for (final Object profile : list(key, value, "Spring profile names")) {
                text(key + " entry", profile);
            }
        } else if ("file_writes".equals(key)) {
            oneOf(key, value, "report", "refuse");
        } else {
            throw new IllegalArgumentException("unknown setting '" + key + "'; known settings: " + KEYS);
        }
    }

    private static Mode modeOf(final Object value) {
        return Mode.valueOf(oneOf("mode", value, "refuse", "report", "off").toUpperCase(Locale.ROOT));
    }

    private static String oneOf(final String key, final Object value, final String... allowed) {
        if (!(value instanceof String) || !Arrays.asList(allowed).contains(value)) {
            throw new IllegalArgumentException(key + " is " + shown(value) + "; it must be one of "
                    + Arrays.asList(allowed));
        }
        return (String) value;
    }

    private static List<?> list(final String key, final Object value, final String what) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(key + " must be a list of " + what + ", not " + shown(value));
        }
        return (List<?>) value;
    }

    private static String target(final int number, final Object entry) {
        final String name = "allow entry " + number;
        if (!(entry instanceof Map)) {
            throw new IllegalArgumentException(name + " (" + shown(entry) + ") needs a target and a reason, "
                    + EXAMPLE);
        }
        final Map<?, ?> keys = (Map<?, ?>) entry;
        onlyEntryKeys(name, keys);
        final String target = text(name + "'s target", keys.get("target"));
        if (!target.equals(target.replaceAll("\\s", ""))) {
            throw new IllegalArgumentException(name + "'s target '" + target + "' has a space in it");
        }
        final Object reason = keys.get("reason");
        if (!(reason instanceof String) || ((String) reason).isEmpty()) {
            throw new IllegalArgumentException(name + " (" + target + ") needs a reason: what the stand-in is, "
                    + EXAMPLE);
        }
        return target;
    }

    private static void onlyEntryKeys(final String name, final Map<?, ?> keys) {
        for (final Object key : keys.keySet()) {
            if (!ENTRY_KEYS.contains(key)) {
                throw new IllegalArgumentException(name + " has '" + key + "'; an entry has only " + ENTRY_KEYS);
            }
        }
    }

    private static String text(final String what, final Object value) {
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException(what + " must be text, not " + shown(value));
        }
        return (String) value;
    }

    private static String shown(final Object value) {
        if (value == null) {
            return "empty";
        }
        return value instanceof String ? "'" + value + "'" : value.toString();
    }

    /** @return the file's mode, or {@code null} if it doesn't set one */
    Mode getMode() {
        return mode;
    }

    /** @return the targets its {@code allow} list names */
    List<String> getAllow() {
        return Collections.unmodifiableList(allow);
    }
}
