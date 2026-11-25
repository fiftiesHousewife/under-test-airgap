package io.github.fiftieshousewife.airgap.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Surefire's JUnit 4 provider is given the listener that names each test, through its {@code listener} property. */
class JUnit4ListenerTest {

    private static final String SUREFIRE = "org.apache.maven.plugins:maven-surefire-plugin";

    @TempDir
    Path folder;

    // Guards a module whose Surefire has one execution with the configuration given, and returns that execution.
    private PluginExecution guarded(final Xpp3Dom configuration, final String dependencyGroup) {
        final PluginExecution test = Sample.execution("default-test", configuration);
        final Plugin surefire = Sample.plugin(SUREFIRE, "2.19.1", null);
        surefire.addExecution(test);
        final MavenProject project = Sample.module(folder.toFile());
        project.getBuild().addPlugin(surefire);
        if (dependencyGroup != null) {
            final Dependency dependency = new Dependency();
            dependency.setGroupId(dependencyGroup);
            dependency.setArtifactId("testng");
            project.getModel().addDependency(dependency);
        }
        TestPlugins.guard(project, Sample.guarding(folder.toFile(), new Properties()));
        return test;
    }

    private static Xpp3Dom listenerProperty(final Object configuration) {
        final Xpp3Dom properties = ((Xpp3Dom) configuration).getChild("properties");
        return properties == null ? null : properties.getChild("property");
    }

    @Test
    void namesTheListenerForJUnit4sOwnRunner() {
        final Xpp3Dom property = listenerProperty(guarded(null, null).getConfiguration());

        assertAll(
                () -> assertThat(Configs.text(property, "name")).isEqualTo("listener"),
                () -> assertThat(Configs.text(property, "value")).isEqualTo(TestPlugins.JUNIT4_LISTENER));
    }

    @Test
    void keepsTheModulesOwnListenersAndAddsOnlyOnce() {
        final Xpp3Dom own = new Xpp3Dom("configuration");
        Configs.addToProperty(own, "listener", "com.example.TimingListener");
        Configs.addToProperty(own, "listener", "com.example.TimingListener");

        final PluginExecution test = guarded(own, null);
        Configs.addToProperty((Xpp3Dom) test.getConfiguration(), "listener", TestPlugins.JUNIT4_LISTENER);

        assertThat(Configs.text(listenerProperty(test.getConfiguration()), "value"))
                .isEqualTo("com.example.TimingListener," + TestPlugins.JUNIT4_LISTENER);
    }

    @Test
    void leavesTestNgsListenersAlone() {
        assertThat(listenerProperty(guarded(null, "org.testng").getConfiguration()))
                .as("TestNG's listener property names TestNG listeners").isNull();
    }
}
