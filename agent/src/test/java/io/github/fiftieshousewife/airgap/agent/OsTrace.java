package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads strace's record of what a JVM did, for the checks the operating system can see: connections and packets to
// other machines, programs started, and native libraries opened from outside the JDK and the system's own folders.
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Test code: the paths are strace's, to classify")
final class OsTrace {

    private static final Pattern NETWORK_CALL = Pattern.compile(
            "^(?:connect|sendto|sendmsg)\\(.*?sa_family=AF_INET6?, sin6?_port=htons\\((\\d+)\\)"
                    + ".*?(?:inet_addr\\(\"([^\"]+)\"\\)|inet_pton\\(AF_INET6, \"([^\"]+)\")");
    private static final Pattern PROGRAM_START = Pattern.compile("^execve\\(\"([^\"]+)\".*\\) = 0$");
    private static final Pattern LIBRARY_OPEN = Pattern.compile(
            "^openat\\([^,]+, \"([^\"]+)\".*\\) = \\d+$");
    private static final Pattern LIBRARY_NAME = Pattern.compile("\\.so(\\.\\d+)*$");
    private static final List<String> LOCAL = Arrays.asList("::1", "0.0.0.0", "::");
    private static final List<String> SYSTEM_FOLDERS = Arrays.asList("/lib/", "/usr/lib/", "/lib64/", "/usr/lib64/");

    private OsTrace() {
    }

    // What one line of the trace says the process did.
    static final class Event {
        final String kind;
        final String target;

        Event(final String kind, final String target) {
            this.kind = kind;
            this.target = target;
        }

        @Override
        public String toString() {
            return kind + " " + target;
        }
    }

    // line: one line of strace's output
    // Returns what it did that the agent should have seen, or null for anything else
    static Event parse(final String line) {
        final Event network = network(line);
        if (network != null) {
            return network;
        }
        final Event program = program(line);
        return program != null ? program : library(line);
    }

    // A connection or packet to another machine
    private static Event network(final String line) {
        final Matcher network = NETWORK_CALL.matcher(line);
        if (!network.find()) {
            return null;
        }
        final String address = network.group(2) != null ? network.group(2) : network.group(3);
        return isLocal(address) ? null : new Event("network", address + ":" + network.group(1));
    }

    // A program started, other than the JDK's own: java, and jspawnhelper, through which JDK 9 and later start others
    // on Linux
    private static Event program(final String line) {
        final Matcher program = PROGRAM_START.matcher(line);
        return program.find() && !isInAJdk(program.group(1)) ? new Event("program", program.group(1)) : null;
    }

    // A native library opened from outside the system's folders and the JDK's
    private static Event library(final String line) {
        final Matcher library = LIBRARY_OPEN.matcher(line);
        final boolean found = library.find() && isLibrary(library.group(1));
        return found && !isTheSystemsOrTheJdks(library.group(1)) ? new Event("library", library.group(1)) : null;
    }

    // A file named as a library is: ending in .so, or .so and a version (libz.so.1.2), not just containing it.
    private static boolean isLibrary(final String path) {
        return LIBRARY_NAME.matcher(path).find();
    }

    private static boolean isLocal(final String address) {
        return address.startsWith("127.") || address.startsWith("::ffff:127.") || LOCAL.contains(address)
                || isThisMachines(address);
    }

    // The trace is read on the machine it was made on, whose own addresses the agent counts as local too.
    private static boolean isThisMachines(final String address) {
        try {
            return HostNames.isThisMachines(InetAddress.getByName(address));
        } catch (UnknownHostException notAnAddress) {
            return false;
        }
    }

    // A library in the system's own folders, a JDK's, or Gradle's.
    private static boolean isTheSystemsOrTheJdks(final String path) {
        for (final String folder : SYSTEM_FOLDERS) {
            if (path.startsWith(folder)) {
                return true;
            }
        }
        return path.contains("/.gradle/native/") || isInAJdk(path);
    }

    // Whether a file is inside a JDK: under a folder with a release file and bin/java.
    private static boolean isInAJdk(final String path) {
        for (File folder = new File(path).getParentFile(); folder != null; folder = folder.getParentFile()) {
            if (new File(folder, "release").isFile() && new File(folder, "bin/java").isFile()) {
                return true;
            }
        }
        return false;
    }
}
