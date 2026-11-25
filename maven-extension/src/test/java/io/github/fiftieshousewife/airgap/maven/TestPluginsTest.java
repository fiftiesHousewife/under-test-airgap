package io.github.fiftieshousewife.airgap.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestPluginsTest {

    private static final String SUREFIRE = "org.apache.maven.plugins:maven-surefire-plugin";

    @TempDir
    Path folder;

    private MavenProject module(final Plugin plugin) {
        final MavenProject project = Sample.module(folder.toFile());
        project.getBuild().addPlugin(plugin);
        return project;
    }

    private void guard(final MavenProject project, final Properties userProperties) {
        TestPlugins.guard(project, Sample.guarding(folder.toFile(), userProperties));
    }

    @Test
    void addsTheAgentAfterWhateverJacocoSetsAsTestsStart() {
        final PluginExecution test = Sample.execution("default-test", null);
        final Plugin surefire = Sample.plugin(SUREFIRE, "3.5.2", null);
        surefire.addExecution(test);
        final MavenProject project = module(surefire);

        guard(project, new Properties());

        final String argLine = Sample.text(test.getConfiguration(), "argLine");
        assertAll(
                () -> assertThat(argLine).startsWith("@{argLine} -Xbootclasspath/a:" + Sample.AGENT.getAbsolutePath()),
                () -> assertThat(argLine).contains("-javaagent:" + Sample.AGENT.getAbsolutePath() + "=report="
                        + new File(folder.toFile(), "target/airgap/surefire-default-test/calls.jsonl").getPath()
                        + ";root=" + folder.toFile().getAbsolutePath() + ";run=test", AgentLine.ALLOW_ATTACH_SELF),
                () -> assertThat(project.getProperties().getProperty("argLine")).as("so @{argLine} is replaced")
                        .isEmpty(),
                () -> assertThat(((Xpp3Dom) test.getConfiguration()).getChild("additionalClasspathElements")
                        .getChild(0).getValue()).isEqualTo(Sample.LISTENER.getAbsolutePath()),
                () -> assertThat(Sample.text(surefire.getConfiguration(), "argLine"))
                        .as("mvn surefire:test").contains("airgap/surefire-default-cli/calls.jsonl"));
    }

    @Test
    void keepsTheModulesOwnArgLine() {
        final PluginExecution test = Sample.execution("default-test", Sample.configuration("argLine", "-Xmx1g"));
        final Plugin failsafe = Sample.plugin("org.apache.maven.plugins:maven-failsafe-plugin", "3.5.2", null);
        failsafe.addExecution(test);

        guard(module(failsafe), new Properties());

        assertThat(Sample.text(test.getConfiguration(), "argLine")).startsWith("-Xmx1g -Xbootclasspath/a:")
                .contains("airgap/failsafe-default-test/calls.jsonl");
    }

    /** Maven 3.6 runs Surefire 2.12.4 when the pom names none, which leaves {@code @{argLine}} as it is. */
    @Test
    void givesAnOldSurefireTheArgLineAsItIsNow() {
        final Plugin surefire = Sample.plugin(SUREFIRE, "2.12.4", null);
        final MavenProject project = module(surefire);
        final Properties commandLine = new Properties();
        commandLine.setProperty("argLine", "-Dfrom.command.line=yes");

        guard(project, commandLine);

        assertThat(Sample.text(surefire.getConfiguration(), "argLine")).startsWith("-Dfrom.command.line=yes -X")
                .doesNotContain("@{");
    }

    @Test
    void leavesTestsThatRunInsideMavenAlone() {
        final Plugin inMaven = Sample.plugin(SUREFIRE, "3.5.2", Sample.configuration("forkCount", "0"));
        final Plugin never = Sample.plugin(SUREFIRE, "2.12.4", Sample.configuration("forkMode", "never"));

        guard(module(inMaven), new Properties());
        guard(module(never), new Properties());

        assertAll(
                () -> assertThat(Sample.text(inMaven.getConfiguration(), "argLine")).isNull(),
                () -> assertThat(Sample.text(never.getConfiguration(), "argLine")).isNull());
    }

    @Test
    void addsTheAgentOnceAndLeavesOtherPluginsAlone() {
        final Plugin surefire = Sample.plugin(SUREFIRE, "3.5.2", null);
        final Plugin other = Sample.plugin("org.apache.maven.plugins:maven-jar-plugin", "3.4.2", null);
        final MavenProject project = module(surefire);
        project.getBuild().addPlugin(other);

        guard(project, new Properties());
        final String once = Sample.text(surefire.getConfiguration(), "argLine");
        guard(project, new Properties());

        assertAll(
                () -> assertThat(Sample.text(surefire.getConfiguration(), "argLine")).isEqualTo(once),
                () -> assertThat(other.getConfiguration()).isNull());
    }

    @Test
    void namesTheModulesSettingsFileAndClearsTheLastReport() throws IOException {
        final Path settings = Files.createDirectories(folder.resolve("testing")).resolve("airgap.yaml");
        Files.write(settings, "mode: report\n".getBytes(StandardCharsets.UTF_8));
        final Path old = Files.createDirectories(folder.resolve("target/airgap/surefire-default-cli"));
        Files.write(old.resolve("calls.jsonl"), "{}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(old.resolve("calls-summary.json"), "{}\n".getBytes(StandardCharsets.UTF_8));
        final Plugin surefire = Sample.plugin(SUREFIRE, "3.5.2", null);

        guard(module(surefire), new Properties());

        assertAll(
                () -> assertThat(Sample.text(surefire.getConfiguration(), "argLine"))
                        .contains(";settings=" + settings.toFile().getAbsolutePath() + ";run=test"),
                () -> assertThat(old.resolve("calls.jsonl")).doesNotExist(),
                () -> assertThat(old.resolve("calls-summary.json")).doesNotExist());
    }

    @Test
    void knowsWhichSurefiresReplacePropertiesLate() {
        assertAll(
                () -> assertThat(TestPlugins.replacesLate("2.17")).isTrue(),
                () -> assertThat(TestPlugins.replacesLate("2.22.2")).isTrue(),
                () -> assertThat(TestPlugins.replacesLate("3.0.0-M5")).isTrue(),
                () -> assertThat(TestPlugins.replacesLate("3")).isTrue(),
                () -> assertThat(TestPlugins.replacesLate("2.16")).isFalse(),
                () -> assertThat(TestPlugins.replacesLate("2.12.4")).isFalse(),
                () -> assertThat(TestPlugins.replacesLate("1.9")).isFalse(),
                () -> assertThat(TestPlugins.replacesLate("${surefire.version}")).isFalse(),
                () -> assertThat(TestPlugins.replacesLate(null)).isFalse());
    }
}
