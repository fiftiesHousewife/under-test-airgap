package io.github.fiftieshousewife.airgap.maven;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.Properties;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

// Modules and plugins as Maven holds them once it has read a pom, built in memory for the tests.
@SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
        justification = "The paths are made up, to compare as text; nothing opens them")
final class Sample {

    static final File AGENT = new File("/repo/airgap-agent-1.0.jar");
    static final File LISTENER = new File("/repo/airgap-junit-1.0.jar");

    private Sample() {
    }

    static MavenProject module(final File folder) {
        final Model model = new Model();
        model.setArtifactId("orders");
        final Build build = new Build();
        build.setDirectory(new File(folder, "target").getPath());
        model.setBuild(build);
        final MavenProject project = new MavenProject(model);
        project.setFile(new File(folder, "pom.xml"));
        return project;
    }

    static Guarding guarding(final File root, final Properties userProperties) {
        return Guarding.builder().agent(AGENT).listener(LISTENER).root(root).userProperties(userProperties).build();
    }

    static Plugin plugin(final String coordinates, final String version, final Xpp3Dom configuration) {
        final String[] parts = coordinates.split(":");
        final Plugin plugin = new Plugin();
        plugin.setGroupId(parts[0]);
        plugin.setArtifactId(parts[1]);
        plugin.setVersion(version);
        plugin.setConfiguration(configuration);
        return plugin;
    }

    static PluginExecution execution(final String id, final Xpp3Dom configuration) {
        final PluginExecution execution = new PluginExecution();
        execution.setId(id);
        execution.setConfiguration(configuration);
        return execution;
    }

    // A configuration with one setting, or none for null.
    static Xpp3Dom configuration(final String name, final String value) {
        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        if (name != null) {
            Configs.set(configuration, name, value);
        }
        return configuration;
    }

    static String text(final Object configuration, final String name) {
        return Configs.text((Xpp3Dom) configuration, name);
    }
}
