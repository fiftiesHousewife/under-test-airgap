package io.github.fiftieshousewife.airgap.maven;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds the agent to a module's Surefire and Failsafe runs, and the JUnit listener to their class path: to the
 * plugin's own configuration (which {@code mvn surefire:test} uses) and to each execution's. The module's own
 * {@code argLine}, such as JaCoCo's, is kept: the agent goes after it. Tests that run inside Maven's own JVM
 * ({@code forkCount=0}) can't be given it, and are warned about.
 */
final class TestPlugins {

    private static final Logger LOG = LoggerFactory.getLogger(TestPlugins.class);
    private static final String GROUP = "org.apache.maven.plugins";
    private static final List<String> PLUGINS = Arrays.asList("maven-surefire-plugin", "maven-failsafe-plugin");
    private static final String ARG_LINE = "argLine";
    /** The name of the plugin's own configuration, which {@code mvn surefire:test} uses. */
    private static final String CLI = "-default-cli";
    /** The first Surefire to replace {@code @{property}} as tests start, after JaCoCo has set its {@code argLine}. */
    private static final int[] LATE_REPLACEMENT = {2, 17};

    private TestPlugins() {
    }

    /**
     * @param project the module
     * @param guarding the build's jars, root and settings
     */
    static void guard(final MavenProject project, final Guarding guarding) {
        for (final Plugin plugin : project.getBuild().getPlugins()) {
            if (GROUP.equals(plugin.getGroupId()) && PLUGINS.contains(plugin.getArtifactId())) {
                final String name = plugin.getArtifactId().replace("maven-", "").replace("-plugin", "");
                final Target target = new Target(project, guarding, plugin);
                plugin.setConfiguration(target.configure(plugin.getConfiguration(), name + CLI));
                for (final PluginExecution execution : plugin.getExecutions()) {
                    execution.setConfiguration(target.configure(execution.getConfiguration(),
                            name + "-" + execution.getId()));
                }
            }
        }
    }

    /** One plugin of one module. */
    private static final class Target {
        private final MavenProject project;
        private final Guarding guarding;
        private final Plugin plugin;

        Target(final MavenProject project, final Guarding guarding, final Plugin plugin) {
            this.project = project;
            this.guarding = guarding;
            this.plugin = plugin;
        }

        @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS",
                justification = "Logged through Maven's own logger; the values are names from the build's own poms")
        Xpp3Dom configure(final Object given, final String name) {
            final Xpp3Dom configuration = Configs.of(given);
            if (runsInMaven(configuration)) {
                // Once per run: the plugin's own configuration warns only if no execution will.
                if (!name.endsWith(CLI) || plugin.getExecutions().isEmpty()) {
                    LOG.warn("airgap: {}'s tests ({}) run inside Maven's own JVM, so the agent isn't on them; run Maven"
                            + " itself under the agent (see the README)", project.getArtifactId(), name);
                }
                return configuration;
            }
            final String own = Configs.text(configuration, ARG_LINE);
            if (own != null && own.contains(guarding.getAgent().getAbsolutePath())) {
                return configuration;
            }
            final GuardedRun run = guarding.run(project, name, "test");
            Configs.set(configuration, ARG_LINE, argLine(own, guarding.line(run)));
            Configs.addItem(Configs.child(configuration, "additionalClasspathElements"), "additionalClasspathElement",
                    guarding.getListener().getAbsolutePath());
            clear(run.getReport());
            return configuration;
        }

        private boolean runsInMaven(final Xpp3Dom configuration) {
            return "0".equals(Configs.text(configuration, "forkCount"))
                    || "never".equals(Configs.text(configuration, "forkMode"));
        }

        /**
         * The module's own {@code argLine}, then the agent. With none, a Surefire that replaces {@code @{argLine}}
         * late keeps what JaCoCo or the command line sets; an older one gets the value the property has now.
         */
        private String argLine(final String own, final String agent) {
            if (own != null) {
                return own + " " + agent;
            }
            final String property = project.getProperties().getProperty(ARG_LINE,
                    guarding.getUserProperties().getProperty(ARG_LINE, ""));
            if (replacesLate(plugin.getVersion())) {
                project.getProperties().setProperty(ARG_LINE, property);
                return "@{argLine} " + agent;
            }
            return (property + " " + agent).trim();
        }
    }

    /**
     * Whether a Surefire or Failsafe version replaces {@code @{property}} as tests start.
     *
     * @param version the plugin's version, such as {@code 2.12.4} or {@code 3.0.0-M5}; {@code null} if not known
     * @return true from 2.17
     */
    static boolean replacesLate(final String version) {
        if (version == null) {
            return false;
        }
        final String[] parts = version.split("[.-]");
        try {
            final int major = Integer.parseInt(parts[0]);
            final int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > LATE_REPLACEMENT[0] || major == LATE_REPLACEMENT[0] && minor >= LATE_REPLACEMENT[1];
        } catch (NumberFormatException unknown) {
            return false;
        }
    }

    /** Clears a run's last report and summary, so each build's report is its own, as each Gradle task's is. */
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
