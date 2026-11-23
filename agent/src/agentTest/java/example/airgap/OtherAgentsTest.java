package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

/**
 * The agent shares the JVM with the other agents tests carry: JaCoCo, which this build attaches to every test task,
 * and Mockito's inline mock maker, which attaches itself and rewrites the classes it mocks, the JDK's among them. Each
 * keeps working, and so do the hooks. PIT is proved in its own run, {@code pitestUnderAgentOnJdk<version>}.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET",
        justification = "The socket aims at an address reserved for documentation, and the agent refuses it")
class OtherAgentsTest {

    private static List<String> linesAbout(final String target) throws IOException {
        final Path report = Paths.get(System.getProperty("airgap.test.report"));
        if (!Files.exists(report)) {
            return new ArrayList<>();
        }
        final List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        lines.removeIf(line -> !line.contains("\"target\":\"" + target + "\""));
        return lines;
    }

    /** A final class, which only the inline mock maker can mock. */
    static final class Prices {
        int priceOf(final String item) {
            return item.length();
        }
    }

    @Test
    void jacocoRecordsCoverageInTheSameJvm() {
        final List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        final List<String> methods = Arrays.asList(Arrays.stream(OtherAgentsTest.class.getDeclaredMethods())
                .map(Method::getName).toArray(String[]::new));

        assertAll(
                () -> assertThat(arguments).anyMatch(argument -> argument.startsWith("-javaagent:")
                        && argument.contains("jacocoagent")),
                () -> assertThat(arguments).anyMatch(argument -> argument.startsWith("-javaagent:")
                        && argument.contains("airgap-agent")),
                () -> assertThat(methods).as("JaCoCo rewrote this class to count its lines").contains("$jacocoInit"));
    }

    @Test
    void mockitosInlineMockMakerMocksFinalClassesAndJdkClassesAndTheHooksStillRefuse() throws IOException {
        final Prices prices = Mockito.mock(Prices.class);
        Mockito.when(prices.priceOf("tea")).thenReturn(250);
        final Socket mocked = Mockito.mock(Socket.class);
        Mockito.when(mocked.isConnected()).thenReturn(true);

        assertAll(
                () -> assertThat(prices.priceOf("tea")).isEqualTo(250),
                () -> assertThat(mocked.isConnected()).isTrue(),
                () -> assertThatCode(() -> mocked.connect(new InetSocketAddress("192.0.2.42", 80), 1000))
                        .as("a mock's connect connects nothing").doesNotThrowAnyException(),
                () -> assertThat(linesAbout("192.0.2.42:80")).as("so it isn't reported").isEmpty(),
                () -> assertThatThrownBy(() -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress("192.0.2.41", 80), 1000);
                    }
                }).as("Mockito rewrote Socket, and its hook is still there")
                        .isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(linesAbout("192.0.2.41:80")).hasSize(1));
    }
}
