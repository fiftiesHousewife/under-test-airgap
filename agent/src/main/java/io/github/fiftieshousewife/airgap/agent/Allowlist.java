package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stand-ins that may be reached: {@code host} (any port), {@code host:port}, {@code process:name} for a program (its
 * file name or full path), or {@code native:name} for a native library (see {@link LibraryNames}); programs and
 * libraries exactly, or with {@code *} wildcards. Names are compared without regard to case. The machine itself is
 * always allowed, and isn't listed here. Nothing allows a test to end the JVM. File writes are allowed by folder, in
 * the settings file's {@code write_folders}, not here.
 */
public final class Allowlist {

    private static final String PROCESS = "process:";
    private static final String NATIVE = "native:";

    private final List<String> entries;

    /**
     * An allowlist.
     *
     * @param entries its entries, as the settings or a test give them
     */
    public Allowlist(final List<String> entries) {
        final List<String> lower = new ArrayList<>();
        for (final String entry : entries) {
            lower.add(Ascii.lowerCase(entry.trim()));
        }
        this.entries = Collections.unmodifiableList(lower);
    }

    /**
     * Whether a call may go ahead.
     *
     * @param kind the kind of call
     * @param target what it tried to reach
     * @return true if an entry allows it
     */
    public boolean allows(final Kind kind, final Target target) {
        if (kind == Kind.EXIT || kind == Kind.FILE_WRITE) {
            return false;
        }
        for (final String entry : entries) {
            if (matches(kind, entry, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(final Kind kind, final String entry, final Target target) {
        final String name = Ascii.lowerCase(target.getName());
        if (kind == Kind.PROCESS) {
            return isProgram(entry, name);
        }
        if (kind == Kind.NATIVE_LIBRARY) {
            return entry.startsWith(NATIVE) && LibraryNames.matches(entry.substring(NATIVE.length()), name);
        }
        return isHost(entry, name, target.getPort());
    }

    private static String fileName(final String path) {
        return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
    }

    private static boolean isProgram(final String entry, final String program) {
        if (!entry.startsWith(PROCESS)) {
            return false;
        }
        final String allowed = entry.substring(PROCESS.length());
        final String file = fileName(program);
        return LibraryNames.wildcardMatch(allowed, file) || LibraryNames.wildcardMatch(allowed + ".exe", file)
                || LibraryNames.wildcardMatch(allowed, program);
    }

    // An entry with one colon names a host and port; with none, or with several (an IPv6 address), only a host.
    private static boolean isHost(final String entry, final String host, final int port) {
        if (!isHostEntry(entry)) {
            return false;
        }
        final int colon = entry.lastIndexOf(':');
        if (colon < 0 || entry.indexOf(':') != colon) {
            return entry.equals(host);
        }
        return entry.substring(0, colon).equals(host) && portMatches(entry.substring(colon + 1), port);
    }

    private static boolean isHostEntry(final String entry) {
        return !entry.startsWith(PROCESS) && !entry.startsWith(NATIVE);
    }

    // A call with no port, such as a lookup, matches any port.
    private static boolean portMatches(final String allowed, final int port) {
        return port < 0 || allowed.equals(String.valueOf(port));
    }

    /** @return the entries, in lower case */
    public List<String> getEntries() {
        return entries;
    }
}
