package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The agent's settings, from the text after {@code =} on its command line: {@code key=value} pairs separated by
 * {@code ;}. Unknown keys stop the JVM at start, so a mistyped setting is never silently ignored.
 *
 * <ul>
 *   <li>{@code mode}: {@code refuse} (the default), {@code report} or {@code off}</li>
 *   <li>{@code report}: where to write the report; {@code build/airgap/calls.jsonl} by default</li>
 *   <li>{@code allow}: comma-separated stand-ins to allow: {@code host}, {@code host:port}, {@code process:name} or
 *       {@code native:name}</li>
 *   <li>{@code run}: what kind of JVM this is, for the report: {@code test} (the default), {@code application} or
 *       {@code build}</li>
 *   <li>{@code project}: comma-separated package prefixes of the project's own code, to name the caller exactly</li>
 *   <li>{@code withoutHooks}: comma-separated hooks not to install, which makes the self-check refuse to start; only
 *       for testing the self-check</li>
 * </ul>
 */
public final class Settings {

    private static final List<String> KEYS = Arrays.asList("mode", "report", "allow", "run", "project", "withoutHooks");

    private Mode mode = Mode.REFUSE;
    private Path report = Paths.get("build", "airgap", "calls.jsonl");
    private Allowlist allowlist = new Allowlist(Collections.<String>emptyList());
    private String run = "test";
    private List<String> project = Collections.emptyList();
    private Set<String> withoutHooks = Collections.emptySet();

    private Settings() {
    }

    /**
     * Reads the settings.
     *
     * @param arguments the agent's arguments, or {@code null} for none
     * @return the settings
     * @throws IllegalArgumentException for an unknown key, a pair without {@code =}, or an unknown mode
     */
    public static Settings parse(final String arguments) {
        final Settings settings = new Settings();
        if (arguments == null || arguments.trim().isEmpty()) {
            return settings;
        }
        for (final String pair : arguments.split(";")) {
            if (!pair.trim().isEmpty()) {
                settings.set(pair.trim());
            }
        }
        return settings;
    }

    private void set(final String pair) {
        final int equals = pair.indexOf('=');
        if (equals < 1) {
            throw new IllegalArgumentException("airgap agent: setting '" + pair + "' needs the form key=value");
        }
        final String key = pair.substring(0, equals).trim();
        final String value = pair.substring(equals + 1).trim();
        if (!KEYS.contains(key)) {
            throw new IllegalArgumentException("airgap agent: unknown setting '" + key + "'; known settings: " + KEYS);
        }
        apply(key, value);
    }

    private void apply(final String key, final String value) {
        if ("mode".equals(key)) {
            mode = modeNamed(value);
        } else if ("report".equals(key)) {
            report = reportPath(value);
        } else if ("allow".equals(key)) {
            allowlist = new Allowlist(list(value));
        } else if ("run".equals(key)) {
            run = value;
        } else if ("project".equals(key)) {
            project = list(value);
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

    /** @return the hooks to leave out, by name; empty except when testing the self-check */
    public Set<String> getWithoutHooks() {
        return Collections.unmodifiableSet(withoutHooks);
    }
}
