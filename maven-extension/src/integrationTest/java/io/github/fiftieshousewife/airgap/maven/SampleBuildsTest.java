package io.github.fiftieshousewife.airgap.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Sample builds run under the extension, named with {@code -Dmaven.ext.class.path} as a one-off run names it, on the
 * latest release of each supported Maven line. Each sample's test connects to an address reserved for documentation
 * and passes only if the agent refused it.
 */
class SampleBuildsTest {

    /** @return each Maven's version and home, as {@code version=home}, shown by its version */
    static Stream<Named<String>> mavens() {
        return Arrays.stream(System.getProperty("airgap.it.mavens").split(","))
                .map(maven -> Named.of(maven.split("=", 2)[0], maven));
    }

    /** A sample, copied to its own folder, built with one Maven. */
    private static final class Build {
        private final Path folder;
        private final String output;
        private final int exit;

        Build(final Path folder, final String output, final int exit) {
            this.folder = folder;
            this.output = output;
            this.exit = exit;
        }

        String report(final String module, final String run) throws IOException {
            final Path report = folder.resolve(module).resolve("target/airgap").resolve(run).resolve("calls.jsonl");
            return Files.exists(report) ? new String(Files.readAllBytes(report), StandardCharsets.UTF_8) : "";
        }
    }

    @SuppressFBWarnings(value = {"COMMAND_INJECTION", "PATH_TRAVERSAL_IN"},
            justification = "Maven and the folders are the ones this repo's build unpacked and names")
    private static Build build(final String maven, final String sample) throws IOException, InterruptedException {
        final String[] versionAndHome = maven.split("=", 2);
        final Path folder = Paths.get(System.getProperty("airgap.it.work"), versionAndHome[0], sample);
        copy(sample, folder);
        final List<String> command = new ArrayList<>(Arrays.asList(
                new File(versionAndHome[1], "bin/mvn").getAbsolutePath(), "-B", "-e", "test",
                "-Dmaven.repo.local=" + System.getProperty("airgap.it.localRepo")));
        // A sample that names the extension in .mvn/extensions.xml gets it from the local repository instead.
        if (!Files.exists(folder.resolve(".mvn/extensions.xml"))) {
            command.add("-Dmaven.ext.class.path=" + System.getProperty("airgap.it.extensionPath"));
        }
        final ProcessBuilder builder = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("airgap.it.javaHome"));
        builder.environment().remove("MAVEN_OPTS");
        final Path log = folder.resolve("build.log");
        builder.redirectOutput(log.toFile());
        final Process process = builder.start();
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly().waitFor();
        }
        return new Build(folder, new String(Files.readAllBytes(log), StandardCharsets.UTF_8), process.exitValue());
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "The samples are this repo's own")
    private static void copy(final String sample, final Path to) throws IOException {
        final Path from = Paths.get(System.getProperty("airgap.it.samples"), sample);
        try (Stream<Path> files = Files.walk(from)) {
            for (final Path file : (Iterable<Path>) files::iterator) {
                final Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    final String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    Files.write(target, text.replace("@airgap.version@", System.getProperty("airgap.it.version"))
                            .getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("mavens")
    void refusesAndReportsATestsCallAndNamesTheTest(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "plain");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).contains("\"kind\":\"tcp\"",
                        "\"target\":\"192.0.2.1:80\"", "\"outcome\":\"refused\"",
                        "\"test\":\"example.PlainTest.refusesAConnectionToAnotherMachine\""),
                () -> assertThat(build.output).contains("airgap: plain made risky calls"));
    }

    /** Maven 3.6 runs Surefire 2.12.4 when the pom names none, which can't replace {@code @{argLine}}. */
    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("mavens")
    void guardsTheSurefireMavenPicksWhenThePomNamesNone(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "old-surefire");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).contains("\"outcome\":\"refused\""));
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("mavens")
    void keepsTheModulesOwnArgLineAndJacoco(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "own-argline");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).contains("\"outcome\":\"refused\""),
                () -> assertThat(build.folder.resolve("target/jacoco.exec")).exists());
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("mavens")
    void givesEachModuleItsOwnReport(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "multi-module");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("one", "surefire-default-test")).contains("ModuleOneTest"),
                () -> assertThat(build.report("two", "surefire-default-test")).contains("ModuleTwoTest"));
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("mavens")
    void worksWhenNamedInTheProjectsExtensionsFile(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "extensions-xml");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).contains("\"outcome\":\"refused\"",
                        "\"test\":\"example.ExtensionsXmlTest.refusesAConnectionToAnotherMachine\""));
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("mavens")
    void warnsAboutTestsThatRunInsideMaven(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "in-maven");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.output).contains("airgap: in-maven's tests (surefire-default-test) run inside"
                        + " Maven's own JVM"),
                () -> assertThat(build.output.split("run inside Maven's own JVM", -1)).as("warned once").hasSize(2));
    }
}
