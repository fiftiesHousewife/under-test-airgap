package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.settings.SettingsFile;
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
import java.util.List;
import java.util.Map;

// Where Settings come from, each read and checked: the command line's key=value pairs, separated by ;, the project's
// settings file, and the folders the settings name, made absolute.
final class SettingsSources {

    static final List<String> KEYS = Arrays.asList("settings", "mode", "report", "allow", "run", "project",
            "withoutHooks", "root");

    private SettingsSources() {
    }

    // Returns the pairs in order, each key known; none for null
    static Map<String, String> pairs(final String arguments) {
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

    // Returns a comma-separated value's items, without spaces around them or empty ones
    static List<String> list(final String value) {
        final List<String> items = new ArrayList<>();
        for (final String item : value.split(",")) {
            if (!item.trim().isEmpty()) {
                items.add(item.trim());
            }
        }
        return Collections.unmodifiableList(items);
    }

    // Returns the list with a comma-separated value's items after it
    static List<String> plus(final List<String> list, final String value) {
        final List<String> joined = new ArrayList<>(list);
        joined.addAll(list(value));
        return Collections.unmodifiableList(joined);
    }

    static Mode modeNamed(final String value) {
        for (final Mode candidate : Mode.values()) {
            if (Ascii.equalsIgnoringCase(candidate.name(), value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("airgap agent: mode '" + value + "' isn't refuse, report or off");
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The engineer running the build chooses where the report goes, on their own machine")
    static Path path(final String value) {
        return Paths.get(value);
    }

    // Reads and checks the project's settings file.
    // Throws IllegalArgumentException saying which file, and what's wrong with it or why it can't be read
    static SettingsFile readFile(final Path file) {
        try {
            return SettingsFile.read(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("airgap agent: can't read the settings file " + file + " ("
                    + unreadable + ")", unreadable);
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("airgap agent: the settings file " + file + " is wrong: "
                    + wrong.getMessage() + ". Its schema is schema/airgap-settings.schema.json", wrong);
        }
    }

    // The folders writes go ahead in unreported: the build's root, the temporary folder, the report's folder, and the
    // settings file's write_folders, relative to the root or, with ~/, to the home folder.
    // Returns each folder's path, absolute
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The engineer running the build names the root folder, on their own machine")
    static List<String> projectFolders(final String root, final Path report, final List<String> writeFolders) {
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
}
