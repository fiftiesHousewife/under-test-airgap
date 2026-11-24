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
 * with the reason it's allowed; the project's own packages, so the report names the project's line that made each
 * call however the agent is started; the folders outside the project its tests may write in
 * ({@code write_folders}), each with its reason; and whether writes elsewhere are refused ({@code file_writes}).
 * {@code production_profiles} is checked, and kept for the hooks that will use it.
 */
final class SettingsFile {

    private static final List<String> KEYS = Arrays.asList("mode", "allow", "packages", "production_profiles",
            "file_writes", "write_folders");
    private static final Entries ALLOW = new Entries("allow entry", "target",
            "what the stand-in is, for example '- target: localhost:8089' with"
                    + " 'reason: WireMock standing in for the postcode service' below it");
    private static final Entries WRITE_FOLDERS = new Entries("write_folders entry", "folder",
            "why the tests write there, for example '- folder: ~/.m2/repository' with"
                    + " 'reason: the tests install the library they build' below it");
    private static final String FOR_EXAMPLE = "for example";

    private Mode mode;
    private final List<String> allow = new ArrayList<>();
    private final List<String> writeFolders = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private boolean refuseFileWrites;

    /** A list whose entries each name one thing and give the reason for it. */
    private static final class Entries {
        private final String name;
        private final String key;
        private final String example;

        Entries(final String name, final String key, final String example) {
            this.name = name;
            this.key = key;
            this.example = example;
        }
    }

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
                final int number = allow.size() + 1;
                allow.add(noSpaces(ALLOW.name + " " + number + "'s target", entry(ALLOW, number, entry)));
            }
        } else if ("write_folders".equals(key)) {
            for (final Object entry : list(key, value, "folders, each with a folder and a reason")) {
                writeFolders.add(entry(WRITE_FOLDERS, writeFolders.size() + 1, entry));
            }
        } else {
            setOther(key, value);
        }
    }

    private void setOther(final String key, final Object value) {
        if ("packages".equals(key)) {
            for (final Object prefix : list(key, value, "package prefixes, such as com.example.")) {
                packages.add(noSpaces("packages entry", text(key + " entry", prefix)));
            }
        } else if ("production_profiles".equals(key)) {
            for (final Object profile : list(key, value, "Spring profile names")) {
                text(key + " entry", profile);
            }
        } else if ("file_writes".equals(key)) {
            refuseFileWrites = "refuse".equals(oneOf(key, value, "report", "refuse"));
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

    private static String entry(final Entries entries, final int number, final Object entry) {
        final String name = entries.name + " " + number;
        if (!(entry instanceof Map)) {
            throw new IllegalArgumentException(name + " (" + shown(entry) + ") needs a " + entries.key
                    + " and a reason, " + entries.example.substring(entries.example.indexOf(FOR_EXAMPLE)));
        }
        final Map<?, ?> keys = (Map<?, ?>) entry;
        onlyEntryKeys(name, entries.key, keys);
        final String named = text(name + "'s " + entries.key, keys.get(entries.key));
        final Object reason = keys.get("reason");
        if (!(reason instanceof String) || ((String) reason).isEmpty()) {
            throw new IllegalArgumentException(name + " (" + named + ") needs a reason: " + entries.example);
        }
        return named;
    }

    private static void onlyEntryKeys(final String name, final String entryKey, final Map<?, ?> keys) {
        for (final Object key : keys.keySet()) {
            if (!entryKey.equals(key) && !"reason".equals(key)) {
                throw new IllegalArgumentException(name + " has '" + key + "'; an entry has only "
                        + Arrays.asList(entryKey, "reason"));
            }
        }
    }

    private static String noSpaces(final String what, final String text) {
        if (!text.equals(text.replaceAll("\\s", ""))) {
            throw new IllegalArgumentException(what + " '" + text + "' has a space in it");
        }
        return text;
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

    /** @return the folders its {@code write_folders} list names, as written */
    List<String> getWriteFolders() {
        return Collections.unmodifiableList(writeFolders);
    }

    /** @return the package prefixes of the project's own code its {@code packages} list names */
    List<String> getPackages() {
        return Collections.unmodifiableList(packages);
    }

    /** @return true if {@code file_writes: refuse} */
    boolean refusesFileWrites() {
        return refuseFileWrites;
    }
}
