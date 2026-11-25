package io.github.fiftieshousewife.airgap.maven;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds the agent to a module's application starts: Spring Boot's {@code run}, through its {@code jvmArguments} (which
 * also make Spring Boot 1 fork), and {@code exec:exec} when the program it starts is {@code java}. {@code exec:java}
 * runs the application inside Maven's own JVM, and is warned about.
 */
final class RunPlugins {

    private static final Logger LOG = LoggerFactory.getLogger(RunPlugins.class);
    private static final String SPRING_BOOT = "org.springframework.boot:spring-boot-maven-plugin";
    private static final String EXEC_PLUGIN = "org.codehaus.mojo:exec-maven-plugin";
    private static final String JVM_ARGUMENTS = "jvmArguments";

    private RunPlugins() {
    }

    /**
     * @param project the module
     * @param guarding the build's jars, root and settings
     * @param goals the goals Maven was asked to run, to warn about {@code exec:java}
     */
    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS",
            justification = "Logged through Maven's own logger; the values are names from the build's own poms")
    static void guard(final MavenProject project, final Guarding guarding, final List<String> goals) {
        for (final Plugin plugin : project.getBuild().getPlugins()) {
            final String key = plugin.getGroupId() + ":" + plugin.getArtifactId();
            if (SPRING_BOOT.equals(key)) {
                final String line = guarding.line(guarding.run(project, "spring-boot-run", "application"));
                plugin.setConfiguration(springBoot(plugin.getConfiguration(), line, guarding));
                for (final PluginExecution execution : plugin.getExecutions()) {
                    execution.setConfiguration(springBoot(execution.getConfiguration(), line, guarding));
                }
            } else if (EXEC_PLUGIN.equals(key)) {
                final GuardedRun run = guarding.run(project, "exec-exec", "application");
                plugin.setConfiguration(exec(plugin.getConfiguration(), run, guarding));
                for (final PluginExecution execution : plugin.getExecutions()) {
                    execution.setConfiguration(exec(execution.getConfiguration(), run, guarding));
                }
            }
        }
        if (goals.contains("exec:java")) {
            LOG.warn("airgap: exec:java runs {} inside Maven's own JVM, so the agent isn't on it; use exec:exec with"
                    + " java, or run Maven itself under the agent (see the README)", project.getArtifactId());
        }
    }

    private static Xpp3Dom springBoot(final Object given, final String line, final Guarding guarding) {
        final Xpp3Dom configuration = Configs.of(given);
        final String own = Configs.text(configuration, JVM_ARGUMENTS);
        final String fromCommandLine = guarding.getUserProperties().getProperty("spring-boot.run.jvmArguments");
        final String before = own == null ? fromCommandLine : own;
        if (before == null || !before.contains(guarding.getAgent().getAbsolutePath())) {
            Configs.set(configuration, JVM_ARGUMENTS, before == null ? line : before + " " + line);
        }
        return configuration;
    }

    /** {@code exec:exec}'s {@code commandlineArgs}, or its {@code arguments} list, get the agent first. */
    private static Xpp3Dom exec(final Object given, final GuardedRun run, final Guarding guarding) {
        final Xpp3Dom configuration = Configs.of(given);
        final String executable = Configs.text(configuration, "executable");
        if (executable == null || !isJava(executable)) {
            return configuration;
        }
        final String commandLine = Configs.text(configuration, "commandlineArgs");
        if (commandLine != null) {
            Configs.set(configuration, "commandlineArgs", guarding.line(run) + " " + commandLine);
        } else {
            Configs.prependItems(Configs.child(configuration, "arguments"), "argument", guarding.arguments(run));
        }
        return configuration;
    }

    /** @return true for {@code java}, {@code java.exe} or a path to either */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "The name is only compared with a fixed ASCII name, as Windows compares file names")
    static boolean isJava(final String executable) {
        final String name = executable.substring(Math.max(executable.lastIndexOf('/'), executable.lastIndexOf('\\'))
                + 1);
        return "java".equals(name) || "java.exe".equalsIgnoreCase(name);
    }
}
