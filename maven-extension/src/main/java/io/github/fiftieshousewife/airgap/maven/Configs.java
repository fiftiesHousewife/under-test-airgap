package io.github.fiftieshousewife.airgap.maven;

import java.util.List;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Reading and changing a plugin's or an execution's {@code <configuration>}, in memory. */
final class Configs {

    private Configs() {
    }

    /**
     * @param configuration a plugin's or execution's configuration, as Maven holds it, or {@code null}
     * @return it, or a new, empty one to set on the plugin or execution
     */
    static Xpp3Dom of(final Object configuration) {
        return configuration instanceof Xpp3Dom ? (Xpp3Dom) configuration : new Xpp3Dom("configuration");
    }

    /** @return the text of a setting, or {@code null} if it isn't set or is empty */
    static String text(final Xpp3Dom configuration, final String name) {
        final Xpp3Dom child = configuration.getChild(name);
        final String value = child == null ? null : child.getValue();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** Sets a setting's text, adding it if it isn't there. */
    static void set(final Xpp3Dom configuration, final String name, final String value) {
        child(configuration, name).setValue(value);
    }

    /** Adds an item to a list setting, such as {@code <additionalClasspathElements>} (see {@link #child}). */
    static void addItem(final Xpp3Dom list, final String item, final String value) {
        final Xpp3Dom added = new Xpp3Dom(item);
        added.setValue(value);
        list.addChild(added);
    }

    /** Puts items at the start of a list setting, such as {@code <arguments>} (see {@link #child}). */
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

    /** @return a setting, such as a list, added empty if it isn't there */
    static Xpp3Dom child(final Xpp3Dom configuration, final String name) {
        Xpp3Dom child = configuration.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            configuration.addChild(child);
        }
        return child;
    }
}
