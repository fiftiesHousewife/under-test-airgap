package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

// The native libraries Gradle and Maven load for themselves are theirs, not the project's: in a test JVM, and in the
// build's own JVM, where the console library is the build tool's too.
class DeciderBuildToolsTest {

    private static Decider decider(final String settings) {
        return new Decider(Settings.parse(settings), call -> { });
    }

    @Test
    void recognisesNativeLibrariesTheBuildToolLoadsForItself() {
        final Decider test = decider("run=test");
        final Decider build = decider("run=build");

        assertAll(
                () -> assertThat(test.isBuildToolsOwn("org.gradle.internal.nativeintegration.NativeServices")).isTrue(),
                () -> assertThat(test.isBuildToolsOwn("net.rubygrapefruit.platform.internal.NativeLibraryLoader"))
                        .isTrue(),
                () -> assertThat(test.isBuildToolsOwn("org.apache.maven.cli.MavenCli")).isTrue(),
                () -> assertThat(build.isBuildToolsOwn("org.fusesource.jansi.internal.JansiLoader")).isTrue(),
                () -> assertThat(test.isBuildToolsOwn("org.fusesource.jansi.internal.JansiLoader"))
                        .as("the console library is the build tool's only in the build JVM").isFalse(),
                () -> assertThat(test.isBuildToolsOwn("io.netty.util.internal.NativeLibraryLoader")).isFalse(),
                () -> assertThat(test.isBuildToolsOwn("org.sqlite.SQLiteJDBCLoader")).isFalse());
    }
}
