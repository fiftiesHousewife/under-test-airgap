package io.github.fiftieshousewife.airgap.gradle;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code airgap { }} block: the agent's settings for every guarded task in the project. Each one becomes a setting
 * on the agent's command line; see the README's settings table.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP",
        justification = "Gradle reads and sets these properties through the getters, so they return the live objects")
public class AirgapExtension {

    private final Property<String> mode;
    private final ListProperty<String> allow;
    private final ListProperty<String> packages;

    /**
     * Created by Gradle.
     *
     * @param objects Gradle's factory for properties
     */
    @Inject
    public AirgapExtension(final ObjectFactory objects) {
        mode = objects.property(String.class);
        mode.convention("refuse");
        allow = objects.listProperty(String.class);
        packages = objects.listProperty(String.class);
    }

    /** @return {@code refuse} (the default), {@code report} or {@code off} */
    public Property<String> getMode() {
        return mode;
    }

    /** @return the stand-ins to allow: {@code host}, {@code host:port} or {@code process:name} */
    public ListProperty<String> getAllow() {
        return allow;
    }

    /** @return package prefixes of the project's own code, so the report names the line that made each call */
    public ListProperty<String> getPackages() {
        return packages;
    }
}
