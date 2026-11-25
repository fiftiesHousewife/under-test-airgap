package io.github.fiftieshousewife.airgap.maven;

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

// Runs a sample build, copied to its own folder, with one Maven, under the extension. The Mavens, the samples and the
// extension's jars are the ones this repo's build unpacked and names, in system properties.
final class SampleBuilds {

    private SampleBuilds() {
    }

    // Returns each Maven's version and home, as version=home, shown by its version
    static Stream<Named<String>> mavens() {
        return Arrays.stream(System.getProperty("airgap.it.mavens").split(","))
                .map(maven -> Named.of(maven.split("=", 2)[0], maven));
    }

    // Returns the report's text for a sample's test, refusesAConnectionToAnotherMachine, in its class
    static String named(final String testClass) {
        return "\"test\":\"example." + testClass + ".refusesAConnectionToAnotherMachine\"";
    }

    // A sample, copied to its own folder, built with one Maven.
    static final class Build {
        final Path folder;
        final String output;
        final int exit;

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

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "The folders are the ones this repo's build names")
    static Build build(final String maven, final String sample) throws IOException, InterruptedException {
        final String[] versionAndHome = maven.split("=", 2);
        final Path folder = Paths.get(System.getProperty("airgap.it.work"), versionAndHome[0], sample);
        copy(sample, folder);
        final Path log = folder.resolve("build.log");
        final int exit = run(command(versionAndHome[1], folder), folder, log);
        return new Build(folder, new String(Files.readAllBytes(log), StandardCharsets.UTF_8), exit);
    }

    // A sample that names the extension in .mvn/extensions.xml gets it from the local repository instead.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Maven is the one this repo's build unpacked")
    private static List<String> command(final String home, final Path folder) {
        final List<String> command = new ArrayList<>(Arrays.asList(new File(home, "bin/mvn").getAbsolutePath(), "-B",
                "-e", "test", "-Dmaven.repo.local=" + System.getProperty("airgap.it.localRepo")));
        if (!Files.exists(folder.resolve(".mvn/extensions.xml"))) {
            command.add("-Dmaven.ext.class.path=" + System.getProperty("airgap.it.extensionPath"));
        }
        return command;
    }

    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "Maven is the one this repo's build unpacked")
    private static int run(final List<String> command, final Path folder, final Path log)
            throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("airgap.it.javaHome"));
        builder.environment().remove("MAVEN_OPTS");
        builder.redirectOutput(log.toFile());
        final Process process = builder.start();
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly().waitFor();
        }
        return process.exitValue();
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
}
