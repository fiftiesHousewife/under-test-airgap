package io.github.fiftieshousewife.airgap.agent.settings;

import io.github.fiftieshousewife.airgap.agent.Mode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A project's settings file, {@code testing/airgap.yaml}, checked by the rules of
 * {@code schema/airgap-settings.schema.json}: its mode, and the stand-ins its {@code allow} list names, each with the
 * reason it's allowed; the project's own {@code packages}, so the report names the project's line that made each call
 * however the agent is started; the folders outside the project its tests may write in ({@code write_folders}), each
 * with its reason; and whether writes elsewhere are refused ({@code file_writes}). {@code production_profiles} is
 * checked, and kept for the hooks that will use it.
 */
public final class SettingsFile {

    private static final SettingsEntries ALLOW = new SettingsEntries("allow entry", "target",
            "what the stand-in is, for example '- target: localhost:8089' with"
                    + " 'reason: WireMock standing in for the postcode service' below it");
    private static final SettingsEntries WRITE_FOLDERS = new SettingsEntries("write_folders entry", "folder",
            "why the tests write there, for example '- folder: ~/.m2/repository' with"
                    + " 'reason: the tests install the library they build' below it");
    private static final Map<String, BiConsumer<SettingsFile, Object>> KEYS = setters();
    public static final List<String> DEFAULT_PRODUCTION_PROFILES = Collections.unmodifiableList(
            Arrays.asList("prod", "production", "live"));

    private Mode mode;
    private final List<String> allow = new ArrayList<>();
    private final List<String> writeFolders = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private List<String> productionProfiles = DEFAULT_PRODUCTION_PROFILES;
    private boolean refuseFileWrites;

    private SettingsFile() {
    }

    private static Map<String, BiConsumer<SettingsFile, Object>> setters() {
        final Map<String, BiConsumer<SettingsFile, Object>> keys = new LinkedHashMap<>();
        keys.put("mode", SettingsFile::setMode);
        keys.put("allow", SettingsFile::setAllow);
        keys.put("packages", SettingsFile::setPackages);
        keys.put("production_profiles", SettingsFile::setProductionProfiles);
        keys.put("file_writes", SettingsFile::setFileWrites);
        keys.put("write_folders", SettingsFile::setWriteFolders);
        return Collections.unmodifiableMap(keys);
    }

    /**
     * Reads and checks a settings file's text.
     *
     * @param text the file's text
     * @return its settings
     * @throws IllegalArgumentException saying what is wrong, and on which line where the YAML itself is wrong
     */
    public static SettingsFile read(final String text) {
        final SettingsFile file = new SettingsFile();
        for (final Map.Entry<String, Object> key : Yaml.read(text).entrySet()) {
            final BiConsumer<SettingsFile, Object> setter = KEYS.get(key.getKey());
            if (setter == null) {
                throw new IllegalArgumentException("unknown setting '" + key.getKey() + "'; known settings: "
                        + new ArrayList<>(KEYS.keySet()));
            }
            setter.accept(file, key.getValue());
        }
        return file;
    }

    private void setMode(final Object value) {
        mode = Mode.valueOf(SettingsEntries.oneOf("mode", value, "refuse", "report", "off").toUpperCase(Locale.ROOT));
    }

    private void setAllow(final Object value) {
        for (final Object entry : SettingsEntries.list("allow", value, "stand-ins, each with a target and a reason")) {
            final int number = allow.size() + 1;
            final String target = ALLOW.entry(number, entry);
            allow.add(SettingsEntries.noSpaces(ALLOW.getName() + " " + number + "'s target", target));
        }
    }

    private void setWriteFolders(final Object value) {
        for (final Object entry : SettingsEntries.list("write_folders", value,
                "folders, each with a folder and a reason")) {
            writeFolders.add(WRITE_FOLDERS.entry(writeFolders.size() + 1, entry));
        }
    }

    private void setPackages(final Object value) {
        for (final Object prefix : SettingsEntries.list("packages", value, "package prefixes, such as com.example.")) {
            packages.add(SettingsEntries.noSpaces("packages entry", SettingsEntries.text("packages entry", prefix)));
        }
    }

    private void setProductionProfiles(final Object value) {
        productionProfiles = new ArrayList<>();
        for (final Object profile : SettingsEntries.list("production_profiles", value, "Spring profile names")) {
            productionProfiles.add(SettingsEntries.text("production_profiles entry", profile));
        }
    }

    private void setFileWrites(final Object value) {
        refuseFileWrites = "refuse".equals(SettingsEntries.oneOf("file_writes", value, "report", "refuse"));
    }

    /** @return the file's mode, or {@code null} if it doesn't set one */
    public Mode getMode() {
        return mode;
    }

    /** @return the targets its {@code allow} list names */
    public List<String> getAllow() {
        return Collections.unmodifiableList(allow);
    }

    /** @return the folders its {@code write_folders} list names, as written */
    public List<String> getWriteFolders() {
        return Collections.unmodifiableList(writeFolders);
    }

    /** @return the package prefixes of the project's own code its {@code packages} list names */
    public List<String> getPackages() {
        return Collections.unmodifiableList(packages);
    }

    /** @return true if {@code file_writes: refuse} */
    public boolean refusesFileWrites() {
        return refuseFileWrites;
    }

    /** @return the Spring profiles that mean production: those the file names, or prod, production and live */
    public List<String> getProductionProfiles() {
        return Collections.unmodifiableList(productionProfiles);
    }
}
