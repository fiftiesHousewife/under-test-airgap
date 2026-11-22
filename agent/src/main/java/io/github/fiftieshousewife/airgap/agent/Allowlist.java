package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stand-ins that may be reached: {@code host} (any port), {@code host:port}, {@code process:name} for a program, or
 * {@code native:name} for a native library. Names are compared without regard to case. The machine itself is always
 * allowed, and isn't listed here. Nothing allows a test to end the JVM.
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
        if (kind == Kind.EXIT) {
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
            return entry.startsWith(NATIVE) && entry.substring(NATIVE.length()).equals(libraryName(name));
        }
        return isHost(entry, name, target.getPort());
    }

    /**
     * A library's name, as {@code loadLibrary} takes it: a path or file name loses its folder, its {@code lib} prefix
     * and its extension, so {@code /tmp/libsqlitejdbc.so} and {@code sqlitejdbc.dll} are both {@code sqlitejdbc}.
     */
    private static String libraryName(final String library) {
        final String file = fileName(library);
        final int dot = file.indexOf('.');
        if (dot < 0) {
            return file;
        }
        final String base = file.substring(0, dot);
        return base.startsWith("lib") && base.length() > 3 ? base.substring(3) : base;
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
        return allowed.equals(file) || (allowed + ".exe").equals(file);
    }

    private static boolean isHost(final String entry, final String host, final int port) {
        if (entry.startsWith(PROCESS) || entry.startsWith(NATIVE)) {
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
