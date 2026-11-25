package io.github.fiftieshousewife.airgap.agent.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// Reads the small part of YAML a settings file needs, since the agent can't depend on a YAML library: keys at the top
// level, each with text, a list in square brackets, or a list of -  items below it; and each item text, or
// keys with text, such as - target: localhost:8089 with reason: ... on the next line. Comments and
// blank lines are skipped. Anything else stops the read with the line it's on, rather than being read as something it
// isn't.
final class Yaml {

    private final List<YamlLine> lines;
    private int at;

    private Yaml(final List<YamlLine> lines) {
        this.lines = lines;
    }

    // Reads a settings file's text.
    //
    // text: the file's text
    // Returns its keys in order, each with a String, a List of Strings or of
    //     Map<String, String>s, or null for no value
    // Throws IllegalArgumentException naming the line, for anything this reader doesn't read
    static Map<String, Object> read(final String text) {
        return new Yaml(YamlLine.all(text)).document();
    }

    private Map<String, Object> document() {
        final Map<String, Object> keys = new LinkedHashMap<>();
        while (at < lines.size()) {
            final YamlLine line = lines.get(at++);
            if (line.getIndent() != 0) {
                throw line.problem("is indented, but nothing above it takes an indented value");
            }
            final Matcher pair = line.pair();
            final String key = pair.group(1);
            checkNew(keys, line, key);
            keys.put(key, value(line, rest(pair)));
        }
        return Collections.unmodifiableMap(keys);
    }

    private static void checkNew(final Map<String, ?> keys, final YamlLine line, final String key) {
        if (keys.containsKey(key)) {
            throw line.problem("repeats the key '" + key + "'");
        }
    }

    private boolean nextIsBelow() {
        return at < lines.size() && lines.get(at).isBelow();
    }

    private Object value(final YamlLine line, final String rest) {
        if (!nextIsBelow()) {
            return line.read(() -> YamlText.value(rest));
        }
        if (!YamlText.isBlank(rest)) {
            throw line.problem("has a value on its own line and a list below it");
        }
        return items(lines.get(at).getIndent());
    }

    private List<Object> items(final int indent) {
        final List<Object> items = new ArrayList<>();
        while (nextIsBelow()) {
            final YamlLine line = lines.get(at++);
            if (line.getIndent() != indent || !line.isItem()) {
                throw line.problem("isn't lined up with the list's other '- ' items");
            }
            items.add(item(line));
        }
        return Collections.unmodifiableList(items);
    }

    private Object item(final YamlLine line) {
        final String content = line.getText().substring(1);
        final String value = content.trim();
        final Matcher pair = YamlLine.KEY.matcher(value);
        if (pair.matches()) {
            return itemKeys(line, pair, line.getIndent() + 1 + content.indexOf(value));
        }
        if (value.isEmpty()) {
            throw line.problem("is an empty '- ' item; write the item on the same line");
        }
        return line.read(() -> YamlText.scalar(value));
    }

    // An item's keys: the one on the item's own line, and those below it, lined up at the column.
    private Map<String, String> itemKeys(final YamlLine line, final Matcher first, final int column) {
        final Map<String, String> keys = new LinkedHashMap<>();
        keys.put(first.group(1), line.read(() -> YamlText.scalar(rest(first))));
        while (at < lines.size() && lines.get(at).getIndent() > line.getIndent()) {
            itemKey(lines.get(at++), column, keys);
        }
        return Collections.unmodifiableMap(keys);
    }

    private static void itemKey(final YamlLine line, final int column, final Map<String, String> keys) {
        if (line.getIndent() != column) {
            throw line.problem("isn't lined up with the key above it");
        }
        final Matcher pair = line.pair();
        checkNew(keys, line, pair.group(1));
        keys.put(pair.group(1), line.read(() -> YamlText.scalar(rest(pair))));
    }

    private static String rest(final Matcher pair) {
        return pair.group(2) == null ? "" : pair.group(2);
    }
}
