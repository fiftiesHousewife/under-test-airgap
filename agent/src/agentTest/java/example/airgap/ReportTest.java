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

    private static List<String> linesAbout(final String target) throws IOException {
        return Files.readAllLines(Paths.get(System.getProperty("airgap.test.report")), StandardCharsets.UTF_8)
                .stream().filter(line -> line.contains("\"target\":\"" + target)).collect(Collectors.toList());
    }

    @Test
    void recordsARefusedCallWithItsCaller() throws IOException {
        final String host = "orders.report-test.example.invalid";
        assertThatThrownBy(() -> InetAddress.getByName(host)).isInstanceOf(UnknownHostException.class);

        final List<String> lines = linesAbout(host);

        assertAll(
                () -> assertThat(lines).hasSize(1),
                () -> assertThat(lines.get(0)).contains("\"kind\":\"host-lookup\"", "\"outcome\":\"refused\"",
                        "\"caller\":\"" + ReportTest.class.getName() + ".lambda"));
    }

    @Test
    void refusesReverseLookupsAsAnUnpluggedMachineWould() throws IOException {
        final InetAddress named = InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 7});
        final InetAddress canonical = InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 8});

        assertAll(
                () -> assertThat(named.getHostName()).as("the address back, as offline").isEqualTo("192.0.2.7"),
                () -> assertThat(canonical.getCanonicalHostName()).isEqualTo("192.0.2.8"),
                () -> assertThat(linesAbout("192.0.2.7")).singleElement().asString()
                        .contains("\"kind\":\"host-lookup\"", "\"outcome\":\"refused\""),
                () -> assertThat(linesAbout("192.0.2.8")).hasSize(1));
    }
}
