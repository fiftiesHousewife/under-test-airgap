package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;

/**
 * The agent refuses to start if a hook isn't in place, and starts when every hook is. Each case starts a fresh JVM
 * of the same version with the agent attached (the agent under test allows starting {@code java}).
 */
@SuppressFBWarnings(value = {"COMMAND_INJECTION"},
        justification = "Starts the test JVM's own java program, with fixed arguments")
class SelfCheckTest {

    @Test
    void startsWhenEveryHookIsInPlace() throws IOException, InterruptedException {
        final Result result = runJava("report=" + new File(System.getProperty("java.io.tmpdir"), "airgap-self.jsonl"));

        assertThat(result.exitCode).as(result.output).isZero();
    }

    /**
     * JDK 24 and later warn when sun.misc.Unsafe is used, or a native library loaded without native access, and
     * HotSpot 17 and later when the bootstrap class path grows after start; the agent, started as the Gradle plugin
     * starts it, and its self-check's probes, mustn't add any warning to anyone's output.
     */
    @Test
    void startsWithoutAWarningFromTheJdk() throws IOException, InterruptedException {
        final String jar = System.getProperty("airgap.test.agent");
        final Result result = run(Paths.java(), "-Xbootclasspath/a:" + jar, "-javaagent:" + jar + "=report="
                + new File(System.getProperty("java.io.tmpdir"), "airgap-self-warnings.jsonl"), "-version");

        assertAll(
                () -> assertThat(result.exitCode).as(result.output).isZero(),
                () -> assertThat(result.output).doesNotContainIgnoringCase("warning")
                        .doesNotContain("sun.misc.Unsafe", "restricted method"));
    }

    /** Test runners end their own JVM once the tests finish; with no test running, an exit goes ahead. */
    @Test
    void letsTheJvmEndWhenNoTestIsRunning() throws IOException, InterruptedException {
        final String agent = "-javaagent:" + System.getProperty("airgap.test.agent") + "=report="
                + new File(System.getProperty("java.io.tmpdir"), "airgap-self-exit.jsonl");
        final Result result = run(Paths.java(), "-cp", System.getProperty("java.class.path"), agent,
                EndsWithSeven.class.getName());

        assertThat(result.exitCode).as(result.output).isEqualTo(EndsWithSeven.STATUS);
    }

    @Test
    void refusesToStartWhenAHookIsMissing() throws IOException, InterruptedException {
        final Result result = runJava("withoutHooks=tcp-socket;report=" + new File(System.getProperty("java.io.tmpdir"),
                "airgap-self-missing.jsonl"));

        assertAll(
                () -> assertThat(result.exitCode).as(result.output).isNotZero(),
                () -> assertThat(result.output).contains("couldn't put its hooks in place for [tcp-socket]"));
    }

    /** The exit hooks can't be probed by exiting, so they're checked another way; leaving one out must still show. */
    @Test
    void refusesToStartWhenTheExitHookIsMissing() throws IOException, InterruptedException {
        final Result result = runJava("withoutHooks=exit;report=" + new File(System.getProperty("java.io.tmpdir"),
                "airgap-self-missing-exit.jsonl"));

        assertAll(
                () -> assertThat(result.exitCode).as(result.output).isNotZero(),
                () -> assertThat(result.output).contains("couldn't put its hooks in place for [exit]"));
    }

    /** No report must mean no risky calls, so a report that can't be written stops the JVM before anything runs. */
    @Test
    void refusesToStartWhenItCantWriteItsReport() throws IOException, InterruptedException {
        final File notAFolder = File.createTempFile("airgap-self-not-a-folder", ".txt");
        try {
            final Result result = runJava("report=" + new File(notAFolder, "calls.jsonl"));

            assertAll(
                    () -> assertThat(result.exitCode).as(result.output).isNotZero(),
                    () -> assertThat(result.output).contains("can't write its report", notAFolder.getPath()));
        } finally {
            Files.delete(notAFolder.toPath());
        }
    }

    private static Result runJava(final String agentSettings) throws IOException, InterruptedException {
        final String agent = "-javaagent:" + System.getProperty("airgap.test.agent") + "=" + agentSettings;
        return run(Paths.java(), agent, "-version");
    }

    private static Result run(final String... command) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(Arrays.asList(command)).redirectErrorStream(true).start();
        final String output = read(process.getInputStream());
        process.waitFor(30, TimeUnit.SECONDS);
        return new Result(process.exitValue(), output);
    }


    private static String read(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Where the running JVM's {@code java} program is. */
    private static final class Paths {
        private Paths() {
        }

        static String java() {
            return new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();
        }
    }

    /** A finished JVM's exit code and everything it printed. */
    private static final class Result {
        private final int exitCode;
        private final String output;

        Result(final int exitCode, final String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
