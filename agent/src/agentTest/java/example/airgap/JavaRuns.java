package example.airgap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

// Fresh JVMs of the same version as the tests', started with the agent attached (the agent under test allows
// starting java), and what each did: its exit code and everything it printed.
@SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "Starts the test JVM's own java program")
final class JavaRuns {

    private JavaRuns() {
    }

    static final class Result {
        final int exitCode;
        final String output;

        Result(final int exitCode, final String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    // The running JVM's java program
    static String java() {
        return new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();
    }

    // java -version, with the agent attached and given these settings
    static Result runJava(final String agentSettings) throws IOException, InterruptedException {
        final String agent = "-javaagent:" + System.getProperty("airgap.test.agent") + "=" + agentSettings;
        return run(java(), agent, "-version");
    }

    static Result run(final String... command) throws IOException, InterruptedException {
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
}
