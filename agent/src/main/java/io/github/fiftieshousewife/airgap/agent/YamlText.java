package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads one value written on one line of a settings file: plain text, text in single or double quotes, or a list in
 * square brackets of such text. A comment ({@code #} after a space) may follow. Anything YAML means more by, such as
 * anchors, tags or text over several lines, is refused, so it is never read as something it isn't.
 */
final class YamlText {

    private static final String SPECIAL = "&*!|>%@`{[]}";

    private final String text;
    private int at;

    private YamlText(final String text) {
        this.text = text;
    }

    /**
     * Reads a value that is plain or quoted text.
     *
     * @param text the value, after its key or list marker
     * @return the text, or {@code null} for nothing, {@code null} or {@code ~}
     * @throws IllegalArgumentException if it isn't text written one of those ways
     */
    static String scalar(final String text) {
        final YamlText reader = new YamlText(text);
        final String value = reader.item(false);
        reader.end();
        return value;
    }

    /**
     * Reads a value that is a list in square brackets, or else text.
     *
     * @param text the value, after its key
     * @return a {@code List<String>} for a list, a {@code String} for text, or {@code null} for nothing
     * @throws IllegalArgumentException if it isn't written one of those ways
     */
    static Object value(final String text) {
        final YamlText reader = new YamlText(text);
        reader.skipSpaces();
        if (reader.at < text.length() && text.charAt(reader.at) == '[') {
            final List<String> list = reader.list();
            reader.end();
            return list;
        }
        return scalar(text);
    }

    /** @return true if the value is nothing but, perhaps, a comment */
    static boolean isBlank(final String text) {
        final String trimmed = text.trim();
        return trimmed.isEmpty() || trimmed.charAt(0) == '#';
    }

    private List<String> list() {
        final List<String> items = new ArrayList<>();
        at++;
        skipSpaces();
        if (peek() == ']') {
            at++;
            return Collections.unmodifiableList(items);
        }
        while (true) {
            items.add(item(true));
            skipSpaces();
            final char next = take();
            if (next == ']') {
                return Collections.unmodifiableList(items);
            }
            if (next != ',') {
                throw new IllegalArgumentException("a list in [ ] needs a comma between its items");
            }
        }
    }

    private String item(final boolean inList) {
        skipSpaces();
        final char first = peek();
        if (first == '"') {
            return doubleQuoted();
        }
        if (first == '\'') {
            return singleQuoted();
        }
        return plain(inList);
    }

    private String plain(final boolean inList) {
        final int start = at;
        while (at < text.length() && !endsPlain(inList)) {
            at++;
        }
        final String value = text.substring(start, at).trim();
        check(value);
        return value.isEmpty() || "~".equals(value) || "null".equals(value) ? null : value;
    }

    private static void check(final String value) {
        if (!value.isEmpty() && SPECIAL.indexOf(value.charAt(0)) >= 0) {
            throw new IllegalArgumentException("'" + value + "' starts with a character YAML gives a meaning to;"
                    + " put it in quotes");
        }
        if (value.contains(": ") || value.endsWith(":")) {
            throw new IllegalArgumentException("'" + value + "' holds a colon and a space; put it in quotes");
        }
    }

    private boolean endsPlain(final boolean inList) {
        final char c = text.charAt(at);
        final boolean comment = c == '#' && (at == 0 || text.charAt(at - 1) == ' ');
        return comment || inList && (c == ',' || c == ']');
    }

    private String doubleQuoted() {
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

    private String singleQuoted() {
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

    private void end() {
        if (!isBlank(text.substring(at))) {
            throw new IllegalArgumentException("'" + text.substring(at).trim() + "' follows the value; only a comment"
                    + " may");
        }
    }

    private void skipSpaces() {
        while (at < text.length() && text.charAt(at) == ' ') {
            at++;
        }
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\n';
    }

    private char take() {
        if (at >= text.length()) {
            throw new IllegalArgumentException("'" + text.trim() + "' ends before its closing quote or bracket");
        }
        return text.charAt(at++);
    }
}
