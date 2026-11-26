package example.airgap.examples;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.gradle.testkit.runner.GradleRunner;

// Runs one example build, copied to a folder of its own, and reads its report's summary, without the report's own
// path, which differs from run to run.
@SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "COMMAND_INJECTION"},
        justification = "The examples, Maven and the folders are the ones this repo's build names")
final class ExampleRuns {

    private ExampleRuns() {
    }

    static Path copy(final String example, final Path to) throws IOException {
        final Path from = Paths.get(System.getProperty("airgap.examples"), example);
        try (Stream<Path> files = Files.walk(from)) {
            for (final Path file : (Iterable<Path>) files::iterator) {
                copyOne(from, file, to.resolve(from.relativize(file).toString()));
            }
        }
        return to;
    }

    private static void copyOne(final Path from, final Path file, final Path target) throws IOException {
        if (Files.isDirectory(file)) {
            Files.createDirectories(target);
        } else if (!from.relativize(file).startsWith("approved")) {
            final String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Files.write(target, text.replace("@airgap.version@", System.getProperty("airgap.examples.version"))
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    static String gradle(final Path build) throws IOException {
        final String javaHome = System.getProperty("airgap.examples.javaHome").replace('\\', '/');
        Files.write(build.resolve("gradle.properties"),
                ("org.gradle.java.home=" + javaHome + "\n").getBytes(StandardCharsets.UTF_8));
        GradleRunner.create().withProjectDir(build.toFile()).forwardOutput().withArguments("test", "--stacktrace",
                "-PairgapRepository=" + System.getProperty("airgap.examples.repo"),
                "-PairgapVersion=" + System.getProperty("airgap.examples.version")).build();
        return summary(build.resolve("build/airgap/test/calls-summary.json"));
    }

    static String maven(final Path build) throws IOException, InterruptedException {
        final ProcessBuilder maven = new ProcessBuilder(Arrays.asList(
                new File(System.getProperty("airgap.examples.mavenHome"), "bin/mvn").getAbsolutePath(), "-B", "test",
                "-Dmaven.repo.local=" + System.getProperty("airgap.examples.mavenRepo")))
                .directory(build.toFile()).redirectErrorStream(true)
                .redirectOutput(build.resolve("build.log").toFile());
        maven.environment().put("JAVA_HOME", System.getProperty("airgap.examples.javaHome"));
        maven.environment().remove("MAVEN_OPTS");
        final Process run = maven.start();
        if (!run.waitFor(15, TimeUnit.MINUTES)) {
            run.destroyForcibly().waitFor();
        }
        return summary(build.resolve("target/airgap/surefire-default-test/calls-summary.json"));
    }

    private static String summary(final Path file) throws IOException {
        final String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        return text.replaceFirst("\\{\"report\":\"[^\"]*\",", "{");
    }

    // The approved summary, written first if this run approves; empty if there's none yet.
    static String approved(final String example, final String tool, final String made) throws IOException {
        final Path file = Paths.get(System.getProperty("airgap.examples"), example, "approved",
                tool + "-calls-summary.json");
        if (Boolean.getBoolean("airgap.examples.approve")) {
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            Files.write(file, made.getBytes(StandardCharsets.UTF_8));
        }
        return Files.exists(file) ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8) : "";
    }
}
