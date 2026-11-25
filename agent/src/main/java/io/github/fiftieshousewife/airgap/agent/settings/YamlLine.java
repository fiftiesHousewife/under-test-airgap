package io.github.fiftieshousewife.airgap.agent.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// One line of a settings file that isn't blank or a comment, with its number, for messages, and its indent.
final class YamlLine {

    static final Pattern KEY = Pattern.compile("([A-Za-z_][A-Za-z0-9_-]*):(?: (.*))?");

    private final int number;
    private final int indent;
    private final String text;

    private YamlLine(final int number, final int indent, final String text) {
        this.number = number;
        this.indent = indent;
        this.text = text;
    }

    // What a line's value is read with.
    interface Read<T> {
        T read();
    }

    // Every line of the text that isn't blank, a comment, or the --- that may start a document.
    static List<YamlLine> all(final String text) {
        final List<YamlLine> lines = new ArrayList<>();
        final String[] raw = text.split("\r?\n", -1);
        for (int i = 0; i < raw.length; i++) {
            final String line = stripEnd(raw[i]);
            if (!skipped(line.trim(), lines.isEmpty())) {
                lines.add(of(i + 1, line));
            }
        }
        return lines;
    }

    private static boolean skipped(final String content, final boolean first) {
        return content.isEmpty() || content.charAt(0) == '#' || first && "---".equals(content);
    }

    private static YamlLine of(final int number, final String line) {
        int indent = 0;
        while (line.charAt(indent) == ' ') {
            indent++;
        }
        final YamlLine read = new YamlLine(number, indent, line.substring(indent));
        if (read.text.charAt(0) == '\t') {
            throw read.problem("is indented with a tab; YAML needs spaces");
        }
        return read;
    }

    private static String stripEnd(final String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    int getIndent() {
        return indent;
    }

    String getText() {
        return text;
    }

    boolean isItem() {
        return "-".equals(text) || text.startsWith("- ");
    }

    // Whether the line belongs to a list below the line above it: indented, or a "- " item.
    boolean isBelow() {
        return indent > 0 || isItem();
    }

    Matcher pair() {
        final Matcher pair = KEY.matcher(text);
        if (!pair.matches()) {
            throw problem("needs the form 'key: value'");
        }
        return pair;
    }

    <T> T read(final Read<T> value) {
        try {
            return value.read();
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("line " + number + ": " + wrong.getMessage(), wrong);
        }
    }

    IllegalArgumentException problem(final String what) {
        return new IllegalArgumentException("line " + number + " " + what);
    }
}
