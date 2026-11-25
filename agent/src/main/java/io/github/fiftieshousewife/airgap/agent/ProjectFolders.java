package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// The folders a run may write in without a report: the build's root, the temporary folder, the report's folder and
// the folders the settings list. Each is kept in the form given and in its real form, since on macOS /var
// and /tmp are links to /private/var and /private/tmp. Writing to the system's null device
// and standard streams writes no file, so it counts as inside too. Paths are compared without regard to case on
// Windows. Nothing here writes, so checking a path can't set off a hook.
final class ProjectFolders {

    private static final List<String> NOT_FILES = Collections.unmodifiableList(Arrays.asList("/dev/null",
            "/dev/stdout", "/dev/stderr", "/dev/tty", "nul"));
    private static final String DEVICE_FILES = "/dev/fd/";

    private final List<String> prefixes = new ArrayList<>();
    private final boolean caseless;

    /**
     * The project's folders.
     *
     * @param folders each folder's path, absolute
     * @param caseless true if the file system ignores case, as Windows' does
     */
    ProjectFolders(final List<String> folders, final boolean caseless) {
        this.caseless = caseless;
        for (final String folder : folders) {
            add(absolute(folder));
            add(real(folder));
        }
    }

    // Returns the folders for this machine, whose file system ignores case on Windows
    static ProjectFolders of(final List<String> folders) {
        return new ProjectFolders(folders, File.separatorChar == '\\');
    }

    private void add(final String folder) {
        final String prefix = withSeparator(folded(folder));
        if (!prefixes.contains(prefix)) {
            prefixes.add(prefix);
        }
    }

    private static String withSeparator(final String folder) {
        return folder.endsWith(File.separator) || folder.endsWith("/") ? folder : folder + File.separator;
    }

    private String folded(final String path) {
        return caseless ? Ascii.lowerCase(path) : path;
    }

    // Whether a path is inside one of the folders, or writes no file.
    //
    // path: the path, absolute (see absolute)
    // Returns true if a write to it goes ahead without a report
    boolean contains(final String path) {
        return writesNoFile(path) || inAFolder(folded(path));
    }

    private static boolean writesNoFile(final String path) {
        return NOT_FILES.contains(Ascii.lowerCase(path)) || path.startsWith(DEVICE_FILES);
    }

    private boolean inAFolder(final String folded) {
        for (final String prefix : prefixes) {
            if (folded.startsWith(prefix) || withSeparator(folded).equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    // A path made absolute against the folder the JVM started in, as the JDK makes it, with . and ..
    // worked out from the text alone. A path the file system can't read is kept as it is.
    //
    // path: the path as the code gave it
    // Returns the path, absolute
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The path is one the code under test is writing, only compared, never opened here")
    static String absolute(final String path) {
        final String absolute = new File(path).getAbsolutePath();
        try {
            return Paths.get(absolute).normalize().toString();
        } catch (InvalidPathException unreadable) {
            return absolute;
        }
    }

    // A folder's real path, through any links; the form given, if it can't be worked out.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The folder is one the engineer's own settings name, only resolved, never opened")
    private static String real(final String folder) {
        try {
            return new File(folder).getCanonicalPath();
        } catch (IOException unreadable) {
            return absolute(folder);
        }
    }

    // Returns each folder, ending with a separator, as compared
    List<String> getPrefixes() {
        return Collections.unmodifiableList(prefixes);
    }
}
