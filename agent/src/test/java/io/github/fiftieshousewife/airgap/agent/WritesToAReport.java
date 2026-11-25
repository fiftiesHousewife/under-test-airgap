package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Paths;

// Run in its own JVM by ReportWritingTest: writes calls to a report shared with other JVMs, then summarises
// it, as a forked test worker does when it ends.
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Test code: the report's path comes from the test")
final class WritesToAReport {

    private WritesToAReport() {
    }

    // Writes the calls.
    //
    // arguments: the report's file, how many calls to write, and a name that makes this JVM's targets its own
    // Throws IOException if a line can't be written
    public static void main(final String[] arguments) throws IOException {
        final JsonLinesReport report = new JsonLinesReport(Paths.get(arguments[0]));
        final int calls = Integer.parseInt(arguments[1]);
        for (int i = 0; i < calls; i++) {
            report.record(Call.builder().kind(Kind.TCP).target(arguments[2] + "-" + i + ".example.invalid:1")
                    .outcome("refused").run("test").javaVersion(System.getProperty("java.version"))
                    .caller("com.example.Worker.call(Worker.java:1)").build());
        }
        report.summarise();
    }
}
