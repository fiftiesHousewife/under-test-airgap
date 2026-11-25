package io.github.fiftieshousewife.airgap.maven;

import static io.github.fiftieshousewife.airgap.maven.SampleBuilds.build;
import static io.github.fiftieshousewife.airgap.maven.SampleBuilds.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.maven.SampleBuilds.Build;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Sample builds run under the extension, named with {@code -Dmaven.ext.class.path} as a one-off run names it, on the
 * latest release of each supported Maven line. Each sample's test connects to an address reserved for documentation
 * and passes only if the agent refused it.
 */
class SampleBuildsTest {

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("io.github.fiftieshousewife.airgap.maven.SampleBuilds#mavens")
    void refusesAndReportsATestsCallAndNamesTheTest(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "plain");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).contains("\"kind\":\"tcp\"",
                        "\"target\":\"192.0.2.1:80\"", "\"outcome\":\"refused\"",
                        named("PlainTest")),
                () -> assertThat(build.output).contains("airgap: plain made risky calls"));
    }

    // Maven 3.6 runs Surefire 2.12.4 when the pom names none, which can't replace {@code @{argLine}}.
    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("io.github.fiftieshousewife.airgap.maven.SampleBuilds#mavens")
    void guardsTheSurefireMavenPicksWhenThePomNamesNone(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "old-surefire");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).as("named by the JUnit 4 listener")
                        .contains("\"outcome\":\"refused\"", named("OldSurefireTest")));
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("io.github.fiftieshousewife.airgap.maven.SampleBuilds#mavens")
    void keepsTheModulesOwnArgLineAndJacoco(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "own-argline");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).contains("\"outcome\":\"refused\"",
                        named("OwnArgLineTest")),
                () -> assertThat(build.folder.resolve("target/jacoco.exec")).exists());
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("io.github.fiftieshousewife.airgap.maven.SampleBuilds#mavens")
    void givesEachModuleItsOwnReport(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "multi-module");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("one", "surefire-default-test")).contains(named("ModuleOneTest")),
                () -> assertThat(build.report("two", "surefire-default-test")).contains(named("ModuleTwoTest")));
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("io.github.fiftieshousewife.airgap.maven.SampleBuilds#mavens")
    void worksWhenNamedInTheProjectsExtensionsFile(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "extensions-xml");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.report("", "surefire-default-test")).contains("\"outcome\":\"refused\"",
                        named("ExtensionsXmlTest")));
    }

    @ParameterizedTest(name = "Maven {0}")
    @MethodSource("io.github.fiftieshousewife.airgap.maven.SampleBuilds#mavens")
    void warnsAboutTestsThatRunInsideMaven(final String maven) throws IOException, InterruptedException {
        final Build build = build(maven, "in-maven");

        assertAll(
                () -> assertThat(build.exit).as(build.output).isZero(),
                () -> assertThat(build.output).contains("airgap: in-maven's tests (surefire-default-test) run inside"
                        + " Maven's own JVM"),
                () -> assertThat(build.output.split("run inside Maven's own JVM", -1)).as("warned once").hasSize(2));
    }
}
