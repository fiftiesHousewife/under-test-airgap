package io.github.fiftieshousewife.airgap.agent;

import java.util.List;

/**
 * Builds one JSON object, field by field. The agent can't depend on a JSON library, since it would share the JVM
 * with the project's own copy.
 */
public final class Json {

    private final StringBuilder out = new StringBuilder("{");

    /**
     * Adds a text field; a {@code null} value leaves the field out.
     *
     * @param name the field's name
     * @param value its value, or {@code null}
     * @return this object
     */
    public Json field(final String name, final String value) {
        if (value != null) {
            name(name).append(quote(value));
        }
        return this;
    }

    /**
     * Adds an object field.
     *
     * @param name the field's name
     * @param value the object
     * @return this object
     */
    public Json object(final String name, final Json value) {
        name(name).append(value);
        return this;
    }

    /**
     * Adds an array of text; an empty list leaves the field out.
     *
     * @param name the field's name
     * @param values the values
     * @return this object
     */
    public Json array(final String name, final List<String> values) {
        if (!values.isEmpty()) {
            final StringBuilder array = name(name).append('[');
            for (int i = 0; i < values.size(); i++) {
                array.append(i == 0 ? "" : ",").append(quote(values.get(i)));
            }
            array.append(']');
        }
        return this;
    }

    private StringBuilder name(final String name) {
        return out.append(out.length() > 1 ? "," : "").append(quote(name)).append(':');
    }

    /**
     * Quotes a text value for JSON.
     *
     * @param value the text
     * @return the text in double quotes, with quotes, backslashes and control characters escaped
     */
    public static String quote(final String value) {
        final StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            quoted.append(escape(value.charAt(i)));
        }
        return quoted.append('"').toString();
    }

    private static String escape(final char c) {
        if (c == '"' || c == '\\') {
            return "\\" + c;
        }
        if (c == '\n') {
            return "\\n";
        }
        if (c == '\r') {
            return "\\r";
        }
        if (c == '\t') {
            return "\\t";
        }
        return c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c);
    }

    @Override
    public String toString() {
        return out + "}";
    }
}
