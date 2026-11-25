package io.github.fiftieshousewife.airgap.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunPluginsTest {

    private static final String SPRING_BOOT = "org.springframework.boot:spring-boot-maven-plugin";
    private static final String EXEC = "org.codehaus.mojo:exec-maven-plugin";

    @TempDir
    Path folder;

    private void guard(final Plugin plugin, final Properties userProperties, final List<String> goals) {
        final MavenProject project = Sample.module(folder.toFile());
        project.getBuild().addPlugin(plugin);
        RunPlugins.guard(project, Sample.guarding(folder.toFile(), userProperties), goals);
    }

    @Test
    void startsSpringBootsRunWithTheAgentAsAnApplication() {
        final Plugin springBoot = Sample.plugin(SPRING_BOOT, "2.7.18", null);
        final PluginExecution start = Sample.execution("start", Sample.configuration("jvmArguments", "-Xmx512m"));
        springBoot.addExecution(start);

        guard(springBoot, new Properties(), Collections.<String>emptyList());

        assertAll(
                () -> assertThat(Sample.text(springBoot.getConfiguration(), "jvmArguments"))
                        .startsWith("-Xbootclasspath/a:").contains("airgap/spring-boot-run/calls.jsonl",
                                ";run=application").doesNotContain(AgentLine.ALLOW_ATTACH_SELF),
                () -> assertThat(Sample.text(start.getConfiguration(), "jvmArguments")).startsWith("-Xmx512m -X"));
    }

    @Test
    void keepsTheCommandLinesJvmArgumentsAndAddsTheAgentOnce() {
        final Plugin springBoot = Sample.plugin(SPRING_BOOT, "3.3.4", null);
        final Properties commandLine = new Properties();
        commandLine.setProperty("spring-boot.run.jvmArguments", "-Dprofile=local");

        guard(springBoot, commandLine, Collections.<String>emptyList());
        final String once = Sample.text(springBoot.getConfiguration(), "jvmArguments");
        guard(springBoot, commandLine, Collections.<String>emptyList());

        assertAll(
                () -> assertThat(once).startsWith("-Dprofile=local -Xbootclasspath/a:"),
                () -> assertThat(Sample.text(springBoot.getConfiguration(), "jvmArguments")).isEqualTo(once));
    }

    @Test
    void putsTheAgentFirstWhenExecStartsJava() {
        final Xpp3Dom configuration = Sample.configuration("executable", "java");
        Configs.addItem(Configs.child(configuration, "arguments"), "argument", "-classpath");
        Configs.addItem(Configs.child(configuration, "arguments"), "argument", "com.example.Main");
        final Plugin exec = Sample.plugin(EXEC, "3.5.0", configuration);
        final PluginExecution run = Sample.execution("run",
                Sample.configuration("executable", "/usr/lib/jvm/bin/java"));
        Configs.set((Xpp3Dom) run.getConfiguration(), "commandlineArgs", "-jar app.jar");
        exec.addExecution(run);

        guard(exec, new Properties(), Collections.<String>emptyList());

        final Xpp3Dom[] arguments = configuration.getChild("arguments").getChildren();
        assertAll(
                () -> assertThat(arguments[0].getValue()).startsWith("-Xbootclasspath/a:"),
                () -> assertThat(arguments[1].getValue()).contains("airgap/exec-exec/calls.jsonl"),
                () -> assertThat(arguments[arguments.length - 1].getValue()).isEqualTo("com.example.Main"),
                () -> assertThat(Sample.text(run.getConfiguration(), "commandlineArgs"))
                        .startsWith("-Xbootclasspath/a:").endsWith(" -jar app.jar"));
    }

    @Test
    void leavesExecAloneWhenItStartsAnotherProgram() {
        final Plugin exec = Sample.plugin(EXEC, "3.5.0", Sample.configuration("executable", "npm"));
        final Plugin unnamed = Sample.plugin(EXEC, "3.5.0", null);

        guard(exec, new Properties(), Collections.singletonList("exec:java"));
        guard(unnamed, new Properties(), Collections.<String>emptyList());

        assertAll(
                () -> assertThat(((Xpp3Dom) exec.getConfiguration()).getChild("arguments")).isNull(),
                () -> assertThat(((Xpp3Dom) unnamed.getConfiguration()).getChildCount()).isZero());
    }

    @Test
    void knowsJava() {
        assertAll(
                () -> assertThat(RunPlugins.isJava("java")).isTrue(),
                () -> assertThat(RunPlugins.isJava("C:\\jdk\\bin\\java.exe")).isTrue(),
                () -> assertThat(RunPlugins.isJava("/opt/jdk/bin/java")).isTrue(),
                () -> assertThat(RunPlugins.isJava("javac")).isFalse(),
                () -> assertThat(RunPlugins.isJava("/opt/java/bin/node")).isFalse());
    }
}
