package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.settings.SettingsFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The agent's settings, from the text after {@code =} on its command line, {@code key=value} pairs separated by
 * {@code ;}, and from the project's settings file. Unknown keys stop the JVM at start, so a mistyped setting is never
 * silently ignored. The keys: {@code settings}, the project's settings file ({@link SettingsFile}), by default
 * {@code testing/airgap.yaml} if it's there, whose {@code mode} counts unless the command line gives one, and to whose
 * stand-ins the command line's {@code allow} adds (a file named but missing, or wrong, stops the JVM); {@code mode},
 * {@code refuse} (the default), {@code report} or {@code off}; {@code report}, where to write the report,
 * {@code build/airgap/calls.jsonl} by default; {@code allow}, comma-separated stand-ins ({@code host},
 * {@code host:port}, {@code process:name} or {@code native:name}); {@code run}, {@code test} (the default),
 * {@code application} or {@code build}; {@code project}, package prefixes of the project's own code, adding to the
 * file's {@code packages}; {@code root}, the build's root folder, inside which writes go ahead unreported, as they do
 * in the temporary folder, the report's folder and the file's {@code write_folders} (relative to the root, or to the
 * home folder with {@code ~/}); and {@code withoutHooks}, hooks not to install, only for testing the self-check.
 */
public final class Settings {

    private static final Path DEFAULT_FILE = Paths.get("testing", "airgap.yaml");
    private static final Map<String, BiConsumer<Settings, String>> SETTERS = settersByKey();

    private Mode mode = Mode.REFUSE;
    private Path report = Paths.get("build", "airgap", "calls.jsonl");
    private List<String> fileAllow = Collections.emptyList();
    private Allowlist allowlist = new Allowlist(fileAllow);
    private String run = "test";
    private List<String> project = Collections.emptyList();
    private Set<String> withoutHooks = Collections.emptySet();
    private String root = System.getProperty("user.dir", ".");
    private List<String> writeFolders = Collections.emptyList();
    private boolean refuseFileWrites;
    private List<String> productionProfiles = SettingsFile.DEFAULT_PRODUCTION_PROFILES;

    private Settings() {
    }

    // What each command-line key but settings sets; settings is read first, as the file allow and project add to.
    private static Map<String, BiConsumer<Settings, String>> settersByKey() {
        final Map<String, BiConsumer<Settings, String>> set = new HashMap<>();
        set.put("mode", (settings, value) -> settings.mode = SettingsSources.modeNamed(value));
        set.put("report", (settings, value) -> settings.report = SettingsSources.path(value));
        set.put("allow", (settings, value) -> settings.allowlist = new Allowlist(
                SettingsSources.plus(settings.fileAllow, value)));
        set.put("run", (settings, value) -> settings.run = value);
        set.put("project", (settings, value) -> settings.project = SettingsSources.plus(settings.project, value));
        set.put("root", (settings, value) -> settings.root = value);
        set.put("withoutHooks",
                (settings, value) -> settings.withoutHooks = new LinkedHashSet<>(SettingsSources.list(value)));
        return Collections.unmodifiableMap(set);
    }

    /**
     * Reads the settings.
     *
     * @param arguments the agent's arguments, or {@code null} for none
     * @return the settings
     * @throws IllegalArgumentException for an unknown key, a pair without {@code =}, an unknown mode, or a
     *     settings file that's named but can't be read, or that's wrong
     */
    public static Settings parse(final String arguments) {
        final Map<String, String> given = SettingsSources.pairs(arguments);
        final Settings settings = new Settings();
        settings.readFile(given.get("settings"));
        given.remove("settings");
        for (final Map.Entry<String, String> pair : given.entrySet()) {
            SETTERS.get(pair.getKey()).accept(settings, pair.getValue());
        }
        return settings;
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The engineer running the build names the project's own settings file")
    private void readFile(final String named) {
        final Path file = named == null ? DEFAULT_FILE : Paths.get(named);
        if (named == null && !Files.isRegularFile(file)) {
            return;
        }
        final SettingsFile read = SettingsSources.readFile(file);
        if (read.getMode() != null) {
            mode = read.getMode();
        }
        fileAllow = read.getAllow();
        allowlist = new Allowlist(fileAllow);
        writeFolders = read.getWriteFolders();
        project = read.getPackages();
        refuseFileWrites = read.refusesFileWrites();
        productionProfiles = read.getProductionProfiles();
    }

    /** @return how the agent behaves */
    public Mode getMode() {
        return mode;
    }

    /** @return where the report is written */
    public Path getReport() {
        return report;
    }

    /** @return the stand-ins allowed by these settings */
    public Allowlist getAllowlist() {
        return allowlist;
    }

    /** @return the kind of JVM, for the report */
    public String getRun() {
        return run;
    }

    /** @return package prefixes of the project's own code; empty if not given */
    public List<String> getProject() {
        return Collections.unmodifiableList(project);
    }

    /** @return true if the settings file says {@code file_writes: refuse} */
    public boolean refusesFileWrites() {
        return refuseFileWrites;
    }

    /** @return the folders writes go ahead in unreported, absolute (see {@code root} and {@code write_folders}) */
    public List<String> getProjectFolders() {
        return SettingsSources.projectFolders(root, report, writeFolders);
    }

    /** @return the Spring profiles that mean production: a context started with one active fails to start */
    public List<String> getProductionProfiles() {
        return Collections.unmodifiableList(productionProfiles);
    }

    /** @return the hooks to leave out, by name; empty except when testing the self-check */
    public Set<String> getWithoutHooks() {
        return Collections.unmodifiableSet(withoutHooks);
    }
}
