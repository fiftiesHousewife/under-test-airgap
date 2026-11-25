package io.github.fiftieshousewife.airgap.agent;

// How a native: entry matches a library: by its name, the file name without its folder, its library extension
// and a leading lib, compared exactly, or with each * in the entry standing for any run of characters.
// Dots inside the name are kept, so a library extracted to sqlite-3.45.1.0-<id>-libsqlitejdbc.so is named
// sqlite-3.45.1.0-<id>-libsqlitejdbc, which *sqlitejdbc matches.
final class LibraryNames {

    private static final String[] EXTENSIONS = {".dll", ".dylib", ".jnilib", ".so"};

    private LibraryNames() {
    }

    // pattern: the entry, after native:
    // library: the library as given to loadLibrary or load, in lower case
    // Returns whether the entry allows it
    static boolean matches(final String pattern, final String library) {
        return wildcardMatch(pattern, nameOf(library));
    }

    // The library's name: /tmp/libsqlitejdbc.so.1 and sqlitejdbc.dll are both sqlitejdbc.
    static String nameOf(final String library) {
        final String file = library.substring(Math.max(library.lastIndexOf('/'), library.lastIndexOf('\\')) + 1);
        final String name = withoutExtension(withoutVersions(file));
        return name.startsWith("lib") && name.length() > 3 ? name.substring(3) : name;
    }

    // The first library extension the name ends with is taken off, if anything is left.
    private static String withoutExtension(final String name) {
        for (final String extension : EXTENSIONS) {
            if (name.endsWith(extension) && name.length() > extension.length()) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }

    // libfoo.so.1.2 loses its .1.2; any other name is left as it is.
    private static String withoutVersions(final String name) {
        int end = name.length();
        int dot = name.lastIndexOf('.', end - 1);
        while (isVersion(name, dot, end)) {
            end = dot;
            dot = name.lastIndexOf('.', end - 1);
        }
        return name.substring(0, end).endsWith(".so") ? name.substring(0, end) : name;
    }

    // Whether the text from the dot to the end is a dot and a number, such as .1.
    private static boolean isVersion(final String name, final int dot, final int end) {
        return dot >= 0 && dot != end - 1 && onlyDigits(name, dot + 1, end);
    }

    private static boolean onlyDigits(final String text, final int from, final int to) {
        for (int i = from; i < to; i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    // Matching with * for any run of characters: the text before the first * starts the name, the text after the last
    // ends it, and the pieces between are found in order, each as early as it can be, which never misses a match.
    static boolean wildcardMatch(final String pattern, final String name) {
        final String[] pieces = pattern.split("\\*", -1);
        if (pieces.length == 1) {
            return pattern.equals(name);
        }
        if (!name.startsWith(pieces[0])) {
            return false;
        }
        final int from = afterMiddlePieces(pieces, name);
        final String last = pieces[pieces.length - 1];
        return from >= 0 && name.length() - from >= last.length() && name.endsWith(last);
    }

    // Returns where the name goes on after the pieces between the first and last *, or -1 if one isn't there
    private static int afterMiddlePieces(final String[] pieces, final String name) {
        int from = pieces[0].length();
        for (int i = 1; i < pieces.length - 1 && from >= 0; i++) {
            final int found = name.indexOf(pieces[i], from);
            from = found < 0 ? -1 : found + pieces[i].length();
        }
        return from;
    }
}
