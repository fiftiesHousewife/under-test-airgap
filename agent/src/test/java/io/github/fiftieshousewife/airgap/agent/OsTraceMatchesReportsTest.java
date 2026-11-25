package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// Run by checkOsTrace in CI, after the agent's tests ran under strace: whatever the operating system saw them do to
// other machines, programs or libraries must be something the agent reported as allowed. Anything else got past it.
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Test code: the folders come from this build")
class OsTraceMatchesReportsTest {

    private static final Pattern LINE = Pattern.compile(
            "\"kind\":\"([^\"]+)\",\"target\":\"([^\"]*)\",\"outcome\":\"([^\"]+)\"");

    // A report line's kind, target and outcome.
    private static final class Reported {
        final String kind;
        final String target;
        final boolean wentAhead;

        Reported(final Matcher line) {
            kind = line.group(1);
            target = line.group(2);
            wentAhead = !"refused".equals(line.group(3));
        }
    }

    private static List<Path> filesUnder(final String folder, final String suffix) throws IOException {
        try (Stream<Path> files = Files.walk(Paths.get(folder))) {
            return files.filter(path -> path.toString().endsWith(suffix)
                    || String.valueOf(path.getFileName()).startsWith(suffix)).collect(Collectors.toList());
        }
    }

    private static List<String> linesOf(final List<Path> files) throws IOException {
        final List<String> lines = new ArrayList<>();
        for (final Path file : files) {
            lines.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
        }
        return lines;
    }

    private static boolean explains(final Reported reported, final OsTrace.Event event) {
        return reported.wentAhead && explainsItsKind(reported, event);
    }

    private static boolean explainsItsKind(final Reported reported, final OsTrace.Event event) {
        switch (event.kind) {
            case "network":
                return explainsNetwork(reported, event);
            case "program":
                return "process".equals(reported.kind)
                        && event.target.endsWith("/" + reported.target.substring(reported.target.lastIndexOf('/') + 1));
            default:
                return "native-library".equals(reported.kind) && event.target.contains(reported.target);
        }
    }

    // A packet to port 53 is a host lookup; any other connection is matched by its port
    private static boolean explainsNetwork(final Reported reported, final OsTrace.Event event) {
        return event.target.endsWith(":53") ? "host-lookup".equals(reported.kind)
                : reported.target.endsWith(event.target.substring(event.target.lastIndexOf(':')));
    }

    private static List<Reported> reported(final List<String> reportLines) {
        final List<Reported> reported = new ArrayList<>();
        for (final String line : reportLines) {
            final Matcher fields = LINE.matcher(line);
            if (fields.find()) {
                reported.add(new Reported(fields));
            }
        }
        return reported;
    }

    // Each event in the trace that no report line explains, with the trace's line
    private static List<String> unexplained(final List<String> trace, final List<Reported> reported) {
        final List<String> unexplained = new ArrayList<>();
        for (final String line : trace) {
            final OsTrace.Event event = OsTrace.parse(line);
            if (event != null && reported.stream().noneMatch(entry -> explains(entry, event))) {
                unexplained.add(event + "    <- " + line);
            }
        }
        return unexplained;
    }

    @Test
    void theOperatingSystemSawNothingTheAgentDidntReport() throws IOException {
        final List<String> trace = linesOf(filesUnder(System.getProperty("airgap.osTrace"), "trace."));
        final List<String> unexplained = unexplained(trace,
                reported(linesOf(filesUnder(System.getProperty("airgap.agentTestReports"), ".jsonl"))));
        assertAll(
                () -> assertThat(trace).as("strace recorded the agent's tests")
                        .anyMatch(line -> line.startsWith("execve(")),
                () -> assertThat(unexplained).as("what the operating system saw that the agent didn't report")
                        .isEmpty());
    }
}
