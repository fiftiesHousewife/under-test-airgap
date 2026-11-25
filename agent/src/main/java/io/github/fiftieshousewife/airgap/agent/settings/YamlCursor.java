package io.github.fiftieshousewife.airgap.agent.settings;

// A position in one value of a settings file, and the reading of text in single or double quotes from it.
final class YamlCursor {

    private final String text;
    private int at;

    YamlCursor(final String text) {
        this.text = text;
    }

    String whole() {
        return text;
    }

    int position() {
        return at;
    }

    boolean more() {
        return at < text.length();
    }

    void advance() {
        at++;
    }

    // Returns what follows the position
    String rest() {
        return text.substring(at);
    }

    void skipSpaces() {
        while (more() && text.charAt(at) == ' ') {
            at++;
        }
    }

    // Returns the character at the position, or a line end past the end
    char peek() {
        return more() ? text.charAt(at) : '\n';
    }

    // Returns the character before the position, or a space at the start
    char before() {
        return at == 0 ? ' ' : text.charAt(at - 1);
    }

    char take() {
        if (!more()) {
            throw new IllegalArgumentException("'" + text.trim() + "' ends before its closing quote or bracket");
        }
        return text.charAt(at++);
    }

    String doubleQuoted() {
        final StringBuilder value = new StringBuilder();
        at++;
        for (char c = take(); c != '"'; c = take()) {
            value.append(c == '\\' ? escaped(take()) : c);
        }
        return value.toString();
    }

    private String escaped(final char c) {
        final int simple = "\"\\/".indexOf(c);
        if (simple >= 0) {
            return String.valueOf(c);
        }
        final int control = "ntr".indexOf(c);
        if (control >= 0) {
            return String.valueOf("\n\t\r".charAt(control));
        }
        if (c == 'u') {
            return unicode();
        }
        throw new IllegalArgumentException("'\\" + c + "' isn't an escape this file can use");
    }

    private String unicode() {
        if (at + 4 > text.length()) {
            throw new IllegalArgumentException("'\\u' needs four hexadecimal digits");
        }
        final String digits = text.substring(at, at + 4);
        at += 4;
        try {
            return String.valueOf((char) Integer.parseInt(digits, 16));
        } catch (NumberFormatException notHex) {
            throw new IllegalArgumentException("'\\u" + digits + "' needs four hexadecimal digits", notHex);
        }
    }

    // A quote inside single quotes is written twice.
    String singleQuoted() {
        final StringBuilder value = new StringBuilder();
        at++;
        while (true) {
            final char c = take();
            if (c == '\'') {
                if (peek() != '\'') {
                    return value.toString();
                }
                at++;
            }
            value.append(c);
        }
    }
}
