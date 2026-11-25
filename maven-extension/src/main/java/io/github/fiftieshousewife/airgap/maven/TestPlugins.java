package io.github.fiftieshousewife.airgap.maven;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Adds the agent to a module's Surefire and Failsafe runs, and the JUnit listener to their class path: to the
// plugin's own configuration (default-cli, which mvn surefire:test uses) and to each execution's. The module's own
// argLine, such as JaCoCo's, is kept: the agent goes after it. JUNIT4_LISTENER, in Surefire's listener property,
// gives JUnit 3 and 4 tests run by JUnit 4 itself their class's @AllowCalls. Tests that run inside Maven's own JVM
// (forkCount=0) can't be given the agent, and are warned about.
final class TestPlugins {

    static final String JUNIT4_LISTENER = "io.github.fiftieshousewife.airgap.junit.AirgapRunListener";
    private static final Logger LOG = LoggerFactory.getLogger(TestPlugins.class);
    private static final String GROUP = "org.apache.maven.plugins";
    private static final List<String> PLUGINS = Arrays.asList("maven-surefire-plugin", "maven-failsafe-plugin");
    private static final String ARG_LINE = "argLine";
    private static final String PLUGINS_OWN_RUN = "-default-cli";

    private TestPlugins() {
    }

    // project: the module
    // guarding: the build's jars, root and settings
    static void guard(final MavenProject project, final Guarding guarding) {
        for (final Plugin plugin : project.getBuild().getPlugins()) {
            if (isTestPlugin(plugin)) {
                new Target(project, guarding, plugin).guard();
            }
        }
    }

    private static boolean isTestPlugin(final Plugin plugin) {
        return GROUP.equals(plugin.getGroupId()) && PLUGINS.contains(plugin.getArtifactId());
    }

    // One plugin of one module.
    private static final class Target {
        private final MavenProject project;
        private final Guarding guarding;
        private final Plugin plugin;

        Target(final MavenProject project, final Guarding guarding, final Plugin plugin) {
            this.project = project;
            this.guarding = guarding;
            this.plugin = plugin;
        }

        void guard() {
            final String name = plugin.getArtifactId().replace("maven-", "").replace("-plugin", "");
            plugin.setConfiguration(configure(plugin.getConfiguration(), name + PLUGINS_OWN_RUN));
            for (final PluginExecution execution : plugin.getExecutions()) {
                execution.setConfiguration(configure(execution.getConfiguration(), name + "-" + execution.getId()));
            }
        }

        Xpp3Dom configure(final Object given, final String name) {
            final Xpp3Dom configuration = Configs.of(given);
            if (runsInMaven(configuration)) {
                warnOnce(name);
            } else if (!hasTheAgent(configuration)) {
                addTheAgent(configuration, name);
            }
            return configuration;
        }

        // Once per run: the plugin's own configuration warns only if no execution will.
        @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS",
                justification = "Logged through Maven's own logger; the values are names from the build's own poms")
        private void warnOnce(final String name) {
            if (!name.endsWith(PLUGINS_OWN_RUN) || plugin.getExecutions().isEmpty()) {
                LOG.warn("airgap: {}'s tests ({}) run inside Maven's own JVM, so the agent isn't on them; run Maven"
                        + " itself under the agent (see the README)", project.getArtifactId(), name);
            }
        }

        private boolean hasTheAgent(final Xpp3Dom configuration) {
            final String own = Configs.text(configuration, ARG_LINE);
            return own != null && own.contains(guarding.getAgent().getAbsolutePath());
        }

        private void addTheAgent(final Xpp3Dom configuration, final String name) {
            final GuardedRun run = guarding.run(project, name, "test");
            Configs.set(configuration, ARG_LINE, argLine(Configs.text(configuration, ARG_LINE), guarding.line(run)));
            Configs.addItem(Configs.child(configuration, "additionalClasspathElements"), "additionalClasspathElement",
                    guarding.getListener().getAbsolutePath());
            // On TestNG, the listener property names TestNG's listeners.
            if (!usesTestNg(project)) {
                Configs.addToProperty(configuration, "listener", JUNIT4_LISTENER);
            }
            clear(run.getReport());
        }

        private boolean runsInMaven(final Xpp3Dom configuration) {
            return "0".equals(Configs.text(configuration, "forkCount"))
                    || "never".equals(Configs.text(configuration, "forkMode"));
        }

        // The module's own argLine, then the agent. With none, a Surefire that replaces @{argLine} late keeps what
        // JaCoCo or the command line sets; an older one gets the value the property has now.
        private String argLine(final String own, final String agent) {
            if (own != null) {
                return own + " " + agent;
            }
            final String property = project.getProperties().getProperty(ARG_LINE,
                    guarding.getUserProperties().getProperty(ARG_LINE, ""));
            if (SurefireVersions.replacesLate(plugin.getVersion())) {
                project.getProperties().setProperty(ARG_LINE, property);
                return "@{argLine} " + agent;
            }
            return (property + " " + agent).trim();
        }
    }

    // Returns true if the module's tests run on TestNG
    static boolean usesTestNg(final MavenProject project) {
        for (final Dependency dependency : project.getDependencies()) {
            if ("org.testng".equals(dependency.getGroupId())) {
                return true;
            }
        }
        return false;
    }

    // Clears a run's last report and summary, so each build's report is its own, as each Gradle task's is.
    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS",
            justification = "Logged through Maven's own logger; the path is the report's, in the target folder")
    static void clear(final File report) {
        final File summary = new File(report.getParentFile(), "calls-summary.json");
        for (final File old : new File[] {report, summary}) {
            if (old.isFile() && !old.delete()) {
                LOG.warn("airgap: couldn't clear {}", old);
            }
        }
    }
}
