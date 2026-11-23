package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the small part of YAML a settings file needs, since the agent can't depend on a YAML library: keys at the top
 * level, each with text, a list in square brackets, or a list of {@code - } items below it; and each item text, or
 * keys with text, such as {@code - target: localhost:8089} with {@code reason: ...} on the next line. Comments and
 * blank lines are skipped. Anything else stops the read with the line it's on, rather than being read as something it
 * isn't.
 */
final class Yaml {

    private static final Pattern KEY = Pattern.compile("([A-Za-z_][A-Za-z0-9_-]*):(?: (.*))?");

    private final List<Line> lines;
    private int at;

    private Yaml(final List<Line> lines) {
        this.lines = lines;
    }

    /**
     * Reads a settings file's text.
     *
     * @param text the file's text
     * @return its keys in order, each with a {@code String}, a {@code List} of {@code String}s or of
     *     {@code Map<String, String>}s, or {@code null} for no value
     * @throws IllegalArgumentException naming the line, for anything this reader doesn't read
     */
    static Map<String, Object> read(final String text) {
        return new Yaml(Line.all(text)).document();
    }

    private Map<String, Object> document() {
        final Map<String, Object> keys = new LinkedHashMap<>();
        while (at < lines.size()) {
            final Line line = lines.get(at++);
            if (line.indent != 0) {
                throw line.problem("is indented, but nothing above it takes an indented value");
            }
            final Matcher pair = line.pair();
            if (keys.containsKey(pair.group(1))) {
                throw line.problem("repeats the key '" + pair.group(1) + "'");
            }
            keys.put(pair.group(1), value(line, rest(pair)));
        }
        return Collections.unmodifiableMap(keys);
    }

    private Object value(final Line line, final String rest) {
        final boolean listBelow = at < lines.size() && (lines.get(at).indent > 0 || lines.get(at).isItem());
        if (!listBelow) {
            return line.read(() -> YamlText.value(rest));
        }
        if (!YamlText.isBlank(rest)) {
            throw line.problem("has a value on its own line and a list below it");
        }
        return items(lines.get(at).indent);
    }

    private List<Object> items(final int indent) {
        final List<Object> items = new ArrayList<>();
        while (at < lines.size() && (lines.get(at).indent > 0 || lines.get(at).isItem())) {
            final Line line = lines.get(at++);
            if (line.indent != indent || !line.isItem()) {
                throw line.problem("isn't lined up with the list's other '- ' items");
            }
            items.add(item(line));
        }
        return Collections.unmodifiableList(items);
    }

    private Object item(final Line line) {
        final String content = line.text.substring(1);
        final String value = content.trim();
        final Matcher pair = KEY.matcher(value);
        if (!pair.matches()) {
            if (value.isEmpty()) {
                throw line.problem("is an empty '- ' item; write the item on the same line");
            }
            return line.read(() -> YamlText.scalar(value));
        }
        final int column = line.indent + 1 + content.indexOf(value);
        final Map<String, String> keys = new LinkedHashMap<>();
        keys.put(pair.group(1), line.read(() -> YamlText.scalar(rest(pair))));
        while (at < lines.size() && lines.get(at).indent > line.indent) {
            itemKey(lines.get(at++), column, keys);
        }
        return Collections.unmodifiableMap(keys);
    }

    private static void itemKey(final Line line, final int column, final Map<String, String> keys) {
        if (line.indent != column) {
            throw line.problem("isn't lined up with the key above it");
        }
        final Matcher pair = line.pair();
        if (keys.containsKey(pair.group(1))) {
            throw line.problem("repeats the key '" + pair.group(1) + "'");
        }
        keys.put(pair.group(1), line.read(() -> YamlText.scalar(rest(pair))));
    }

    private static String rest(final Matcher pair) {
        return pair.group(2) == null ? "" : pair.group(2);
    }

    /** What a line's value is read with. */
    private interface Read<T> {
        T read();
    }

    /** One line that isn't blank or a comment. */
    private static final class Line {

        private final int number;
        private final int indent;
        private final String text;

        private Line(final int number, final int indent, final String text) {
            this.number = number;
            this.indent = indent;
            this.text = text;
        }

        static List<Line> all(final String text) {
            final List<Line> lines = new ArrayList<>();
            final String[] raw = text.split("\r?\n", -1);
            for (int i = 0; i < raw.length; i++) {
                final String line = stripEnd(raw[i]);
                final String content = line.trim();
                final boolean skipped = content.isEmpty() || content.charAt(0) == '#'
                        || lines.isEmpty() && "---".equals(content);
                if (!skipped) {
                    lines.add(of(i + 1, line));
                }
            }
            return lines;
        }

        private static Line of(final int number, final String line) {
            int indent = 0;
            while (line.charAt(indent) == ' ') {
                indent++;
            }
            final Line read = new Line(number, indent, line.substring(indent));
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

        boolean isItem() {
            return "-".equals(text) || text.startsWith("- ");
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
}
