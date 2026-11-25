package io.github.fiftieshousewife.airgap.maven;

import java.util.Arrays;
import java.util.List;
import org.codehaus.plexus.util.xml.Xpp3Dom;

// Reading and changing a plugin's or an execution's <configuration>, in memory.
final class Configs {

    private Configs() {
    }

    // configuration: a plugin's or execution's configuration, as Maven holds it, or null
    // Returns it, or a new, empty one to set on the plugin or execution
    static Xpp3Dom of(final Object configuration) {
        return configuration instanceof Xpp3Dom ? (Xpp3Dom) configuration : new Xpp3Dom("configuration");
    }

    // Returns the text of a setting, or null if it isn't set or is empty
    static String text(final Xpp3Dom configuration, final String name) {
        final Xpp3Dom child = configuration.getChild(name);
        final String value = child == null ? null : child.getValue();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    // Sets a setting's text, adding it if it isn't there.
    static void set(final Xpp3Dom configuration, final String name, final String value) {
        child(configuration, name).setValue(value);
    }

    // Adds an item to a list setting, such as <additionalClasspathElements> (see child).
    static void addItem(final Xpp3Dom list, final String item, final String value) {
        final Xpp3Dom added = new Xpp3Dom(item);
        added.setValue(value);
        list.addChild(added);
    }

    // Puts items at the start of a list setting, such as <arguments> (see child).
    static void prependItems(final Xpp3Dom listed, final String item, final List<String> values) {
        final Xpp3Dom[] existing = listed.getChildren();
        for (int i = existing.length - 1; i >= 0; i--) {
            listed.removeChild(i);
        }
        for (final String value : values) {
            final Xpp3Dom added = new Xpp3Dom(item);
            added.setValue(value);
            listed.addChild(added);
        }
        for (final Xpp3Dom kept : existing) {
            listed.addChild(kept);
        }
    }

    // Adds a value to one of Surefire's <properties>, such as listener: after the values it has, with a
    // comma, or as a new property.
    static void addToProperty(final Xpp3Dom configuration, final String name, final String value) {
        final Xpp3Dom properties = child(configuration, "properties");
        final Xpp3Dom property = property(properties, name);
        if (property == null) {
            final Xpp3Dom added = new Xpp3Dom("property");
            set(added, "name", name);
            set(added, "value", value);
            properties.addChild(added);
        } else {
            addValue(property, value);
        }
    }

    private static Xpp3Dom property(final Xpp3Dom properties, final String name) {
        for (final Xpp3Dom property : properties.getChildren("property")) {
            if (name.equals(text(property, "name"))) {
                return property;
            }
        }
        return null;
    }

    private static void addValue(final Xpp3Dom property, final String value) {
        final String values = text(property, "value");
        if (values == null || values.trim().isEmpty()) {
            set(property, "value", value);
        } else if (!Arrays.asList(values.split("\\s*,\\s*")).contains(value)) {
            set(property, "value", values + "," + value);
        }
    }

    // Returns a setting, such as a list, added empty if it isn't there
    static Xpp3Dom child(final Xpp3Dom configuration, final String name) {
        Xpp3Dom child = configuration.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            configuration.addChild(child);
        }
        return child;
    }
}
