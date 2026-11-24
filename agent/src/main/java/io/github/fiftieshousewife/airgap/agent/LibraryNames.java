package io.github.fiftieshousewife.airgap.agent;

/**
 * How a {@code native:} entry matches a library: by its name, the file name without its folder, its library extension
 * and a leading {@code lib}, compared exactly, or with each {@code *} in the entry standing for any run of characters.
 * Dots inside the name are kept, so a library extracted to {@code sqlite-3.45.1.0-<id>-libsqlitejdbc.so} is named
 * {@code sqlite-3.45.1.0-<id>-libsqlitejdbc}, which {@code *sqlitejdbc} matches.
 */
final class LibraryNames {

    private static final String[] EXTENSIONS = {".dll", ".dylib", ".jnilib", ".so"};

    private LibraryNames() {
    }

    /**
     * @param pattern the entry, after {@code native:}
     * @param library the library as given to {@code loadLibrary} or {@code load}, in lower case
     * @return whether the entry allows it
     */
    static boolean matches(final String pattern, final String library) {
        return wildcardMatch(pattern, nameOf(library));
    }

    /** The library's name: {@code /tmp/libsqlitejdbc.so.1} and {@code sqlitejdbc.dll} are both {@code sqlitejdbc}. */
    static String nameOf(final String library) {
        String name = library.substring(Math.max(library.lastIndexOf('/'), library.lastIndexOf('\\')) + 1);
        name = withoutVersions(name);
        for (final String extension : EXTENSIONS) {
            if (name.endsWith(extension) && name.length() > extension.length()) {
                name = name.substring(0, name.length() - extension.length());
                break;
            }
        }
        return name.startsWith("lib") && name.length() > 3 ? name.substring(3) : name;
    }

    /** {@code libfoo.so.1.2} loses its {@code .1.2}; any other name is left as it is. */
    private static String withoutVersions(final String name) {
        int end = name.length();
        while (true) {
            final int dot = name.lastIndexOf('.', end - 1);
            if (dot < 0 || dot == end - 1 || !onlyDigits(name, dot + 1, end)) {
                break;
            }
            end = dot;
        }
        return name.substring(0, end).endsWith(".so") ? name.substring(0, end) : name;
    }

    private static boolean onlyDigits(final String text, final int from, final int to) {
        for (int i = from; i < to; i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Matching with {@code *} for any run of characters: on a mismatch, the last {@code *} seen takes one more
     * character and the match goes on from there.
     */
    static boolean wildcardMatch(final String pattern, final String name) {
        int p = 0;
        int n = 0;
        int star = -1;
        int resume = 0;
        while (n < name.length()) {
            if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                resume = n;
            } else if (p < pattern.length() && pattern.charAt(p) == name.charAt(n)) {
                p++;
                n++;
            } else if (star >= 0) {
                p = star + 1;
                n = ++resume;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }
}
