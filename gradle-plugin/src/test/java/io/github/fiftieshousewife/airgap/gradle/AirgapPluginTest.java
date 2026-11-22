package io.github.fiftieshousewife.airgap.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class AirgapPluginTest {

    @TempDir
    Path folder;

    private Project project;
    private File agentJar;

    @BeforeEach
    void applyThePlugin() throws IOException {
        project = ProjectBuilder.builder().withProjectDir(folder.toFile()).build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(AirgapPlugin.class);
        agentJar = Files.createFile(folder.resolve("airgap-agent.jar")).toFile();
        final Configuration agent = project.getConfigurations().getByName(AirgapPlugin.AGENT_CONFIGURATION);
        agent.getDependencies().clear();
        agent.getDependencies().add(project.getDependencies().create(project.files(agentJar)));
    }

    @org.junit.jupiter.api.Test
    void theTestTaskStartsWithTheAgentAndItsReport() {
        final String report = project.getLayout().getBuildDirectory().file("airgap/test/calls.jsonl").get().getAsFile()
                .getAbsolutePath();
        assertThat(arguments((Test) project.getTasks().getByName("test"))).containsExactly(
                "-Xbootclasspath/a:" + agentJar.getAbsolutePath(),
                "-javaagent:" + agentJar.getAbsolutePath() + "=report=" + report + ";mode=refuse;run=test");
    }

    @org.junit.jupiter.api.Test
    void aJavaExecTaskStartsWithTheAgentAsAnApplication() {
        final JavaExec run = project.getTasks().register("bootRun", JavaExec.class).get();
        assertThat(String.join(" ", arguments(run)))
                .contains("build/airgap/bootRun/calls.jsonl;mode=refuse;run=application");
    }

    @org.junit.jupiter.api.Test
    void theSettingsBlockReachesTheAgent() {
        final AirgapExtension settings = project.getExtensions().getByType(AirgapExtension.class);
        settings.getMode().set("report");
        settings.getAllow().add("wiremock.test:8089");
        settings.getAllow().add("process:git");
        settings.getPackages().add("com.example.");
        assertThat(String.join(" ", arguments((Test) project.getTasks().getByName("test"))))
                .endsWith(";mode=report;run=test;allow=wiremock.test:8089,process:git;project=com.example.");
    }

    @org.junit.jupiter.api.Test
    void theTestsGetTheJUnitListenerAndTheAgentComesFromItsOwnConfiguration() {
        final Project fresh = ProjectBuilder.builder().build();
        fresh.getPluginManager().apply(AirgapPlugin.class);
        fresh.getPluginManager().apply("java");
        assertAll(
                () -> assertThat(coordinates(fresh, "testImplementation"))
                        .containsExactly("io.github.fiftieshousewife.airgap:airgap-junit:" + AirgapPlugin.version()),
                () -> assertThat(coordinates(fresh, AirgapPlugin.AGENT_CONFIGURATION))
                        .containsExactly("io.github.fiftieshousewife.airgap:airgap-agent:" + AirgapPlugin.version()),
                () -> assertThat(AirgapPlugin.version()).isNotBlank().doesNotContain("$"));
    }

    @org.junit.jupiter.api.Test
    void eachRunStartsWithAnEmptyReport() throws IOException {
        final Test test = (Test) project.getTasks().getByName("test");
        final Path report = project.getLayout().getBuildDirectory().file("airgap/test/calls.jsonl").get().getAsFile()
                .toPath();
        Files.createDirectories(java.util.Objects.requireNonNull(report.getParent()));
        Files.write(report, "{}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        test.getActions().get(0).execute(test);
        assertThat(report).doesNotExist();
    }

    private static List<String> arguments(final JavaForkOptions options) {
        final List<String> arguments = new ArrayList<>();
        for (final CommandLineArgumentProvider provider : options.getJvmArgumentProviders()) {
            provider.asArguments().forEach(arguments::add);
        }
        return arguments;
    }

    private static List<String> coordinates(final Project project, final String configuration) {
        final List<String> coordinates = new ArrayList<>();
        for (final Dependency dependency : project.getConfigurations().getByName(configuration).getDependencies()) {
            coordinates.add(dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion());
        }
        return coordinates;
    }
}
