package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** A refused call is written to the report, with what was tried and the line of the project's code that tried it. */
class ReportTest {

    @Test
    void recordsARefusedCallWithItsCaller() throws IOException {
        final String host = "orders.report-test.example.invalid";
        assertThatThrownBy(() -> InetAddress.getByName(host)).isInstanceOf(UnknownHostException.class);

        final List<String> lines = Files.readAllLines(Paths.get(System.getProperty("airgap.test.report")),
                StandardCharsets.UTF_8).stream().filter(line -> line.contains(host)).collect(Collectors.toList());

        assertAll(
                () -> assertThat(lines).hasSize(1),
                () -> assertThat(lines.get(0)).contains("\"kind\":\"host-lookup\"", "\"outcome\":\"refused\"",
                        "\"caller\":\"" + ReportTest.class.getName() + ".lambda"));
    }
}
