package example.airgap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// The lines of the report the agent writes during these tests, as the build names it in airgap.test.report.
final class ReportLines {

    private ReportLines() {
    }

    static List<String> matching(final Predicate<String> wanted) throws IOException {
        return Files.readAllLines(Paths.get(System.getProperty("airgap.test.report")), StandardCharsets.UTF_8)
                .stream().filter(wanted).collect(Collectors.toList());
    }

    // Lines whose target starts with the text: a host, a host:port, or a file's path
    static List<String> about(final String target) throws IOException {
        return matching(line -> line.contains("\"target\":\"" + target));
    }

    // Lines whose target is exactly the text
    static List<String> aboutExactly(final String target) throws IOException {
        return matching(line -> line.contains("\"target\":\"" + target + "\""));
    }

    // File-write lines whose target contains the text
    static List<String> fileWritesAbout(final String text) throws IOException {
        return matching(line -> line.contains("\"kind\":\"file-write\"") && line.contains(text));
    }
}
