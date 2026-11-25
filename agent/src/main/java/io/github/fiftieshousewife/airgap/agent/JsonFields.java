package io.github.fiftieshousewife.airgap.agent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Reads the top-level fields of one JSON object, such as a line of the agent's report, as text: enough to summarise
// the report without a JSON library, which the agent can't depend on. Nested objects and arrays are skipped; numbers,
// true, false and null are read as their text.
final class JsonFields {

    private final String text;
    private int at;

    private JsonFields(final String text) {
        this.text = text;
    }

    // Reads one object's fields.
    //
    // line: the object, on one line
    // Returns its top-level fields other than objects and arrays; empty if the line isn't one whole JSON object, as a
    //     report's last line may not be when a JVM was killed while writing it
    static Map<String, String> of(final String line) {
        try {
            return new JsonFields(line).object();
        } catch (IllegalArgumentException | IndexOutOfBoundsException notWhole) {
            return Collections.emptyMap();
        }
    }

    private Map<String, String> object() {
        final Map<String, String> fields = new HashMap<>();
        expect('{');
        if (peek() != '}') {
            field(fields);
        }
        while (peek() != '}') {
            expect(',');
            field(fields);
        }
        expect('}');
        if (!text.substring(at).trim().isEmpty()) {
            throw new IllegalArgumentException("more after the object");
        }
        return fields;
    }

    // One name and its value; a value that is an object or an array is skipped.
    private void field(final Map<String, String> fields) {
        final String name = string();
        expect(':');
        final String value = value();
        if (value != null) {
            fields.put(name, value);
        }
    }

    // Returns the value as text, or null for an object or an array, which are skipped
    private String value() {
        final char first = peek();
        if (first == '"') {
            return string();
        }
        if (first == '{' || first == '[') {
            skipNested();
            return null;
        }
        final int start = at;
        while (",}".indexOf(text.charAt(at)) < 0) {
            at++;
        }
        return text.substring(start, at).trim();
    }

    private void skipNested() {
        int depth = 0;
        do {
            final char c = text.charAt(at);
            if (c == '"') {
                string();
            } else {
                depth += depthChange(c);
                at++;
            }
        } while (depth > 0);
    }

    // Returns 1 for an opening bracket or brace, -1 for a closing one, 0 for anything else
    private static int depthChange(final char c) {
        if ("{[".indexOf(c) >= 0) {
            return 1;
        }
        return "}]".indexOf(c) >= 0 ? -1 : 0;
    }

    private String string() {
        expect('"');
        final StringBuilder out = new StringBuilder();
        for (char c = text.charAt(at++); c != '"'; c = text.charAt(at++)) {
            out.append(c == '\\' ? unescape() : c);
        }
        return out.toString();
    }

    // \n, \r, \t, \b and \f stand for control characters and \\uXXXX for any; any other escaped character for itself.
    private char unescape() {
        final char c = text.charAt(at++);
        final int control = "nrtbf".indexOf(c);
        if (control >= 0) {
            return "\n\r\t\b\f".charAt(control);
        }
        if (c == 'u') {
            at += 4;
            return (char) Integer.parseInt(text.substring(at - 4, at), 16);
        }
        return c;
    }

    private char peek() {
        while (Character.isWhitespace(text.charAt(at))) {
            at++;
        }
        return text.charAt(at);
    }

    private void expect(final char wanted) {
        if (peek() != wanted) {
            throw new IllegalArgumentException("expected " + wanted + " at " + at);
        }
        at++;
    }
}
