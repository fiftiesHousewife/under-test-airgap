package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The agent's settings, from the text after {@code =} on its command line: {@code key=value} pairs separated by
 * {@code ;}, and from the project's settings file. Unknown keys stop the JVM at start, so a mistyped setting is never
 * silently ignored.
 *
 * <ul>
 *   <li>{@code settings}: the project's settings file ({@link SettingsFile}); {@code testing/airgap.yaml} by default,
 *       read if it's there. Its {@code mode} counts unless the command line gives one, and the command line's
 *       {@code allow} adds to its stand-ins. A file that's named but missing, or that's wrong, stops the JVM</li>
 *   <li>{@code mode}: {@code refuse} (the default), {@code report} or {@code off}</li>
 *   <li>{@code report}: where to write the report; {@code build/airgap/calls.jsonl} by default</li>
 *   <li>{@code allow}: comma-separated stand-ins to allow: {@code host}, {@code host:port}, {@code process:name} or
 *       {@code native:name}</li>
 *   <li>{@code run}: what kind of JVM this is, for the report: {@code test} (the default), {@code application} or
 *       {@code build}</li>
 *   <li>{@code project}: comma-separated package prefixes of the project's own code, to name the caller exactly;
 *       they add to the settings file's {@code packages}</li>
 *   <li>{@code root}: the build's root folder, inside which writes go ahead unreported; the folder the JVM starts in
 *       by default. The temporary folder, the report's folder and the settings file's {@code write_folders} (relative
 *       to the root, or to the home folder with {@code ~/}) count too</li>
 *   <li>{@code withoutHooks}: comma-separated hooks not to install, which makes the self-check refuse to start; only
 *       for testing the self-check</li>
 * </ul>
 */
public final class Settings {

    private static final List<String> KEYS = Arrays.asList("settings", "mode", "report", "allow", "run", "project",
            "withoutHooks", "root");
    private static final Path DEFAULT_FILE = Paths.get("testing", "airgap.yaml");

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

    private Settings() {
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
        final Map<String, String> given = pairs(arguments);
        final Settings settings = new Settings();
        settings.readFile(given.get("settings"));
        for (final Map.Entry<String, String> pair : given.entrySet()) {
            settings.apply(pair.getKey(), pair.getValue());
        }
        return settings;
    }

    private static Map<String, String> pairs(final String arguments) {
        final Map<String, String> pairs = new LinkedHashMap<>();
        if (arguments == null) {
            return pairs;
        }
        for (final String pair : arguments.split(";")) {
            if (!pair.trim().isEmpty()) {
                add(pairs, pair.trim());
            }
        }
        return pairs;
    }

    private static void add(final Map<String, String> pairs, final String pair) {
        final int equals = pair.indexOf('=');
        if (equals < 1) {
            throw new IllegalArgumentException("airgap agent: setting '" + pair + "' needs the form key=value");
        }
        final String key = pair.substring(0, equals).trim();
        if (!KEYS.contains(key)) {
            throw new IllegalArgumentException("airgap agent: unknown setting '" + key + "'; known settings: " + KEYS);
        }
        pairs.put(key, pair.substring(equals + 1).trim());
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The engineer running the build names the project's own settings file")
    private void readFile(final String named) {
        final Path file = named == null ? DEFAULT_FILE : Paths.get(named);
        if (named == null && !Files.isRegularFile(file)) {
            return;
        }
        final SettingsFile read;
        try {
            read = SettingsFile.read(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("airgap agent: can't read the settings file " + file + " ("
                    + unreadable + ")", unreadable);
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("airgap agent: the settings file " + file + " is wrong: "
                    + wrong.getMessage() + ". Its schema is schema/airgap-settings.schema.json", wrong);
        }
        if (read.getMode() != null) {
            mode = read.getMode();
        }
        fileAllow = read.getAllow();
        allowlist = new Allowlist(fileAllow);
        writeFolders = read.getWriteFolders();
        project = read.getPackages();
        refuseFileWrites = read.refusesFileWrites();
    }

    private void apply(final String key, final String value) {
        if ("settings".equals(key)) {
            return;
        }
        if ("mode".equals(key)) {
            mode = modeNamed(value);
        } else if ("report".equals(key)) {
            report = reportPath(value);
        } else if ("allow".equals(key)) {
            final List<String> entries = new ArrayList<>(fileAllow);
            entries.addAll(list(value));
            allowlist = new Allowlist(entries);
        } else if ("run".equals(key)) {
            run = value;
        } else if ("project".equals(key)) {
            final List<String> prefixes = new ArrayList<>(project);
            prefixes.addAll(list(value));
            project = Collections.unmodifiableList(prefixes);
        } else if ("root".equals(key)) {
            root = value;
        } else {
            withoutHooks = new LinkedHashSet<>(list(value));
        }
    }

    private static Mode modeNamed(final String value) {
        for (final Mode candidate : Mode.values()) {
            if (Ascii.equalsIgnoringCase(candidate.name(), value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("airgap agent: mode '" + value + "' isn't refuse, report or off");
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The engineer running the build chooses where the report goes, on their own machine")
    private static Path reportPath(final String value) {
        return Paths.get(value);
    }

    private static List<String> list(final String value) {
        final List<String> items = new ArrayList<>();
        for (final String item : value.split(",")) {
            if (!item.trim().isEmpty()) {
                items.add(item.trim());
            }
        }
        return Collections.unmodifiableList(items);
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

    /**
     * The folders writes go ahead in unreported: the build's root, the temporary folder, the report's folder, and the
     * settings file's {@code write_folders}, relative to the root or, with {@code ~/}, to the home folder.
     *
     * @return each folder's path, absolute
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The engineer running the build names the root folder, on their own machine")
    public List<String> getProjectFolders() {
        final List<String> folders = new ArrayList<>();
        final File rootFolder = new File(root).getAbsoluteFile();
        folders.add(rootFolder.getPath());
        folders.add(System.getProperty("java.io.tmpdir", rootFolder.getPath()));
        final Path reportFolder = report.toAbsolutePath().getParent();
        if (reportFolder != null) {
            folders.add(reportFolder.toString());
        }
        for (final String folder : writeFolders) {
            folders.add(resolved(rootFolder, folder).getPath());
        }
        return Collections.unmodifiableList(folders);
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The project's own settings file names folders on the engineer's own machine")
    private static File resolved(final File rootFolder, final String folder) {
        if (folder.startsWith("~/") || folder.startsWith("~\\")) {
            return new File(System.getProperty("user.home", "."), folder.substring(2));
        }
        final File given = new File(folder);
        return given.isAbsolute() ? given : new File(rootFolder, folder);
    }

    /** @return the hooks to leave out, by name; empty except when testing the self-check */
    public Set<String> getWithoutHooks() {
        return Collections.unmodifiableSet(withoutHooks);
    }
}
