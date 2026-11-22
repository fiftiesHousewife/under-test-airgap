package io.github.fiftieshousewife.airgap.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs a sample build under the plugin on the latest release of each supported major Gradle version, each on a JDK
 * that Gradle supports, and checks the tests and an application start both ran with the agent. The sample build
 * resolves everything from a local repository that this build publishes, so it needs no network.
 */
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Test code: the paths come from this build")
class SampleBuildTest {

    @TempDir
    Path sample;

    static Stream<String[]> gradleVersions() {
        final List<String[]> versions = new ArrayList<>();
        for (final String pair : System.getProperty("airgap.it.gradleVersions").split(",")) {
            versions.add(pair.split("=", 2));
        }
        versions.add(new String[] {"current", System.getProperty("airgap.it.currentJdk")});
        return versions.stream();
    }

    @ParameterizedTest(name = "Gradle {0}")
    @MethodSource("gradleVersions")
    void theTestsAndTheApplicationRunWithTheAgent(final String gradle, final String jdk) throws IOException {
        SampleBuild.write(sample, jdk);
        final BuildResult result = runner(gradle).withArguments("test", "runSample", "--stacktrace").build();
        final String testReport = read("build/airgap/test/calls.jsonl");
        final String runReport = read("build/airgap/runSample/calls.jsonl");
        assertAll(
                () -> assertThat(result.getOutput()).contains("refused by the airgap agent"),
                () -> assertThat(testReport).contains("\"target\":\"sample-test.invalid\"", "\"outcome\":\"refused\"",
                        "\"run\":{\"kind\":\"test\"", "\"caller\":\"sample.SampleTest.lookupIsRefused("),
                () -> assertThat(runReport).contains("\"target\":\"sample-main.invalid\"",
                        "\"run\":{\"kind\":\"application\"", "\"caller\":\"sample.Main.main("));
    }

    private GradleRunner runner(final String gradle) {
        final GradleRunner runner = GradleRunner.create().withProjectDir(sample.toFile()).forwardOutput();
        if ("current".equals(gradle)) {
            return runner;
        }
        final String distributions = System.getProperty("airgap.it.distributions");
        final File local = distributions == null ? null : new File(distributions, "gradle-" + gradle + "-bin.zip");
        return local != null && local.isFile() ? runner.withGradleDistribution(local.toURI())
                : runner.withGradleVersion(gradle);
    }

    private String read(final String path) throws IOException {
        return new String(Files.readAllBytes(sample.resolve(path)), StandardCharsets.UTF_8);
    }
}
