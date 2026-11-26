package io.github.fiftieshousewife.airgap.agent;

import java.util.List;
import java.util.Locale;

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
     * Adds a whole-number field.
     *
     * @param name the field's name
     * @param value its value
     * @return this object
     */
    public Json number(final String name, final long value) {
        name(name).append(value);
        return this;
    }

    /**
     * Adds an object field; {@code null} leaves the field out.
     *
     * @param name the field's name
     * @param value the object
     * @return this object
     */
    public Json object(final String name, final Json value) {
        if (value != null) {
            name(name).append(value);
        }
        return this;
    }

    /**
     * Adds a field that is {@code true}; {@code false} leaves the field out.
     *
     * @param name the field's name
     * @param value whether to add it
     * @return this object
     */
    public Json flag(final String name, final boolean value) {
        if (value) {
            name(name).append("true");
        }
        return this;
    }

    /**
     * Adds an array of text; an empty list, or {@code null}, leaves the field out.
     *
     * @param name the field's name
     * @param values the values
     * @return this object
     */
    public Json array(final String name, final List<String> values) {
        if (values != null && !values.isEmpty()) {
            appendArray(name(name), values);
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

    // A quote, a backslash, a line end or a tab is written with a backslash; any other control character as \\uXXXX.
    private static String escape(final char c) {
        final int named = "\"\\\n\r\t".indexOf(c);
        if (named >= 0) {
            return "\\" + "\"\\nrt".charAt(named);
        }
        return c < 0x20 ? String.format(Locale.ROOT, "\\u%04x", (int) c) : String.valueOf(c);
    }

    @Override
    public String toString() {
        return out + "}";
    }

    private static void appendArray(final StringBuilder out, final List<String> values) {
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            out.append(i == 0 ? "" : ",").append(quote(values.get(i)));
        }
        out.append(']');
    }
}
