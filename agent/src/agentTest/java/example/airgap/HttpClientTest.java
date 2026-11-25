package example.airgap;

import static example.airgap.JdkHttpClient.failureOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// The JDK's HTTP client (JDK 11 and later) runs requests on its own threads; a call it makes is still named by the
// test and the line that sent the request. Reached by reflection, since these tests compile for Java 8. The client is
// made, and its threads started, before any test runs, as a client in a static field is.
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class HttpClientTest {

    private static final String TEST = "example.HttpClientTest.sendsARequest";
    private static Object client;

    @BeforeAll
    static void makeTheClientBeforeAnyTest() throws ReflectiveOperationException {
        if (JdkHttpClient.present()) {
            client = JdkHttpClient.client();
            JdkHttpClient.sendAsync(client, "http://192.0.2.20:80/warm-up").handle((response, failure) -> null).join();
        }
    }

    @Test
    void namesTheTestAndTheSenderOfARefusedRequest() {
        assumeTrue(JdkHttpClient.present(), "the HTTP client is in JDK 11 and later");
        final Throwable sent;
        final Throwable sentAsync;
        Gate.enterTest(TEST);
        try {
            sent = failureOf(() -> JdkHttpClient.send(client, "http://192.0.2.21:80/orders"));
            sentAsync = failureOf(() -> JdkHttpClient.sendAsync(client, "http://192.0.2.22:80/orders").get());
        } finally {
            Gate.leaveTest();
        }
        assertAll(
                () -> assertThat(sent).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(sentAsync).isInstanceOf(ConnectException.class),
                () -> assertSentByThisTest("192.0.2.21:80"),
                () -> assertSentByThisTest("192.0.2.22:80"));
    }

    private static void assertSentByThisTest(final String target) throws IOException {
        assertThat(ReportLines.about(target)).isNotEmpty().allSatisfy(line -> assertThat(line)
                .contains("\"kind\":\"tcp\"", "\"test\":\"" + TEST + "\"",
                        "\"caller\":\"" + JdkHttpClient.class.getName() + ".send"));
    }
}
