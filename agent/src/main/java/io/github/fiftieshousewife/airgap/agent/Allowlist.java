package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Stand-ins that may be reached: {@code host} (any port), {@code host:port}, or {@code process:name} for a program.
 * Names are compared without regard to case. The machine itself is always allowed, and isn't listed here.
 */
public final class Allowlist {

    private static final String PROCESS = "process:";

    private final List<String> entries;

    /**
     * An allowlist.
     *
     * @param entries its entries, as the settings or a test give them
     */
    public Allowlist(final List<String> entries) {
        final List<String> lower = new ArrayList<>();
        for (final String entry : entries) {
            lower.add(entry.trim().toLowerCase(Locale.ROOT));
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
        final String name = target.getName().toLowerCase(Locale.ROOT);
        for (final String entry : entries) {
            if (kind == Kind.PROCESS ? isProgram(entry, name) : isHost(entry, name, target.getPort())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProgram(final String entry, final String program) {
        if (!entry.startsWith(PROCESS)) {
            return false;
        }
        final String allowed = entry.substring(PROCESS.length());
        final String file = program.substring(Math.max(program.lastIndexOf('/'), program.lastIndexOf('\\')) + 1);
        return allowed.equals(file) || (allowed + ".exe").equals(file);
    }

    private static boolean isHost(final String entry, final String host, final int port) {
        if (entry.startsWith(PROCESS)) {
            return false;
        }
        final int colon = entry.lastIndexOf(':');
        if (colon < 0 || entry.indexOf(':') != colon) {
            return entry.equals(host);
        }
        final boolean sameHost = entry.substring(0, colon).equals(host);
        return sameHost && (port < 0 || entry.substring(colon + 1).equals(String.valueOf(port)));
    }

    /** @return the entries, in lower case */
    public List<String> getEntries() {
        return entries;
    }
}
