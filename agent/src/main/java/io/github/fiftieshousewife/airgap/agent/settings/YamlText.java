package io.github.fiftieshousewife.airgap.agent.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Reads one value written on one line of a settings file: plain text, text in single or double quotes, or a list in
// square brackets of such text. A comment (# after a space) may follow. Anything YAML means more by, such as
// anchors, tags or text over several lines, is refused, so it is never read as something it isn't.
final class YamlText {

    private static final String SPECIAL = "&*!|>%@`{[]}";
    private static final List<String> NOTHING = Arrays.asList("", "~", "null");

    private final YamlCursor cursor;

    private YamlText(final String text) {
        this.cursor = new YamlCursor(text);
    }

    // Reads a value that is plain or quoted text.
    //
    // text: the value, after its key or list marker
    // Returns the text, or null for nothing, null or ~
    // Throws IllegalArgumentException if it isn't text written one of those ways
    static String scalar(final String text) {
        final YamlText reader = new YamlText(text);
        final String value = reader.item(false);
        reader.end();
        return value;
    }

    // Reads a value that is a list in square brackets, or else text.
    //
    // text: the value, after its key
    // Returns a List<String> for a list, a String for text, or null for nothing
    // Throws IllegalArgumentException if it isn't written one of those ways
    static Object value(final String text) {
        final YamlText reader = new YamlText(text);
        reader.cursor.skipSpaces();
        if (reader.cursor.peek() != '[') {
            return scalar(text);
        }
        final List<String> list = reader.list();
        reader.end();
        return list;
    }

    // Returns true if the value is nothing but, perhaps, a comment
    static boolean isBlank(final String text) {
        final String trimmed = text.trim();
        return trimmed.isEmpty() || trimmed.charAt(0) == '#';
    }

    private List<String> list() {
        cursor.advance();
        cursor.skipSpaces();
        if (cursor.peek() == ']') {
            cursor.advance();
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(items());
    }

    private List<String> items() {
        final List<String> items = new ArrayList<>();
        for (char next = ','; next != ']'; next = cursor.take()) {
            if (next != ',') {
                throw new IllegalArgumentException("a list in [ ] needs a comma between its items");
            }
            items.add(item(true));
            cursor.skipSpaces();
        }
        return items;
    }

    private String item(final boolean inList) {
        cursor.skipSpaces();
        final char first = cursor.peek();
        if (first == '"') {
            return cursor.doubleQuoted();
        }
        return first == '\'' ? cursor.singleQuoted() : plain(inList);
    }

    private String plain(final boolean inList) {
        final int start = cursor.position();
        while (cursor.more() && !endsPlain(inList)) {
            cursor.advance();
        }
        final String value = cursor.whole().substring(start, cursor.position()).trim();
        checkFirst(value);
        checkColon(value);
        return NOTHING.contains(value) ? null : value;
    }

    private static void checkFirst(final String value) {
        if (!value.isEmpty() && SPECIAL.indexOf(value.charAt(0)) >= 0) {
            throw new IllegalArgumentException("'" + value + "' starts with a character YAML gives a meaning to;"
                    + " put it in quotes");
        }
    }

    private static void checkColon(final String value) {
        if (value.contains(": ") || value.endsWith(":")) {
            throw new IllegalArgumentException("'" + value + "' holds a colon and a space; put it in quotes");
        }
    }

    // A comment starts with # at the start or after a space; in a list, a comma or ] ends an item too.
    private boolean endsPlain(final boolean inList) {
        final char c = cursor.peek();
        final boolean comment = c == '#' && cursor.before() == ' ';
        return comment || inList && (c == ',' || c == ']');
    }

    private void end() {
        if (!isBlank(cursor.rest())) {
            throw new IllegalArgumentException("'" + cursor.rest().trim() + "' follows the value; only a comment"
                    + " may");
        }
    }
}
