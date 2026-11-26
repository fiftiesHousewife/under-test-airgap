package io.github.fiftieshousewife.airgap.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.JavaForkOptions;

/**
 * Starts every {@link Test} task, and every {@link JavaExec} task (which includes Spring Boot's {@code bootRun}),
 * with the airgap agent, and gives the tests the JUnit listener. Each task's report goes to
 * {@code build/airgap/<task name>/calls.jsonl}. The agent's jar is held in the configuration named
 * {@link #AGENT_CONFIGURATION}. Uses only Gradle APIs that Gradle 5.6 has.
 */
public final class AirgapPlugin implements Plugin<Project> {

    public static final String AGENT_CONFIGURATION = "airgapAgent";

    private static final String GROUP = "io.github.fiftieshousewife.airgap";

    @Override
    public void apply(final Project project) {
        final AirgapExtension settings = project.getExtensions().create("airgap", AirgapExtension.class);
        final String version = version();
        final Configuration agent = project.getConfigurations().create(AGENT_CONFIGURATION, configuration -> {
            configuration.setDescription("The airgap agent's jar, added to every test run and application start");
            configuration.setTransitive(false);
            configuration.setCanBeConsumed(false);
        });
        project.getDependencies().add(AGENT_CONFIGURATION, GROUP + ":airgap-agent:" + version);
        project.getPlugins().withType(JavaPlugin.class, java -> project.getDependencies()
                .add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, GROUP + ":airgap-junit:" + version));
        final Guard guard = new Guard(project, agent, settings);
        project.getTasks().withType(Test.class).configureEach(test -> {
            guard.apply(test, test, "test");
            test.getOutputs().file(guard.report(test)).withPropertyName("airgapReport");
        });
        project.getTasks().withType(JavaExec.class).configureEach(run -> guard.apply(run, run, "application"));
    }

    static String version() {
        final Properties properties = new Properties();
        try (InputStream in = AirgapPlugin.class.getResourceAsStream("airgap-plugin.properties")) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("airgap: can't read the plugin's version", e);
        }
        return properties.getProperty("version");
    }

    // Adds the agent to one task.
    private static final class Guard {

        private final Project project;
        private final Configuration agent;
        private final AirgapExtension settings;

        Guard(final Project project, final Configuration agent, final AirgapExtension settings) {
            this.project = project;
            this.agent = agent;
            this.settings = settings;
        }

        Provider<RegularFile> report(final Task task) {
            return project.getLayout().getBuildDirectory().file("airgap/" + task.getName() + "/calls.jsonl");
        }

        void apply(final Task task, final JavaForkOptions options, final String run) {
            final Provider<RegularFile> report = report(task);
            task.doFirst(new ClearReport(report));
            options.getJvmArgumentProviders().add(AgentArguments.builder()
                    .agent(agent)
                    .mode(settings.getMode())
                    .allow(settings.getAllow())
                    .packages(settings.getPackages())
                    .run(run)
                    .report(report)
                    .settingsFile(project.files("testing/airgap.yaml"))
                    .rootDir(project.getRootDir())
                    .build());
        }
    }
}
