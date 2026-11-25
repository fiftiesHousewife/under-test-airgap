package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

// Behind an allowed HTTP proxy on the machine itself, the JDK's own HTTP clients are judged by their real destination
// as well as the proxy: a destination that isn't allowed is refused before the proxy is reached, and reported with
// both; an allowed one goes ahead through the proxy.
class ProxiedRequestsTest {

    private static final String REFUSED_DESTINATION = "behind-the-proxy.example.invalid";
    private static final String ALLOWED_URL = "http://allowed.example.invalid:8443/";

    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD",
            justification = "The tests' own fixed URLs, through a stand-in proxy on the machine itself")
    private static HttpURLConnection through(final StandInProxy proxy, final String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection(new Proxy(Proxy.Type.HTTP, proxy.address()));
    }

    @Test
    void refusesAUrlConnectionsDestinationBehindTheProxy() throws IOException {
        try (StandInProxy proxy = new StandInProxy()) {
            assertAll(
                    () -> assertThatThrownBy(() -> through(proxy, "http://" + REFUSED_DESTINATION + "/").connect())
                            .isInstanceOf(UnknownHostException.class)
                            .hasMessageContaining(REFUSED_DESTINATION + ":80: refused by the airgap agent"),
                    () -> assertThat(ReportLines.aboutExactly(REFUSED_DESTINATION + ":80"))
                            .anyMatch(line -> line.contains("\"proxy\":\"" + proxy.hostAndPort() + "\"")),
                    () -> assertThat(proxy.connections()).as("refused before the proxy is reached").isZero());
        }
    }

    @Test
    void letsAUrlConnectionToAnAllowedDestinationGoThroughTheProxy() throws IOException {
        try (StandInProxy proxy = new StandInProxy()) {
            assertThat(through(proxy, ALLOWED_URL).getResponseCode()).isEqualTo(200);
        }
    }

    @Test
    void refusesTheJdkClientsDestinationBehindTheProxy() throws IOException, ReflectiveOperationException {
        assumeTrue(JdkHttpClient.present(), "the JDK's HTTP client is in JDK 11 and later");
        try (StandInProxy proxy = new StandInProxy()) {
            final Object client = JdkHttpClient.clientThrough(proxy.selector());
            assertAll(
                    () -> assertThat(JdkHttpClient.failureOf(() -> JdkHttpClient.send(client,
                            "http://" + REFUSED_DESTINATION + ":8080/"))).isInstanceOf(UnknownHostException.class)
                            .hasMessageContaining(REFUSED_DESTINATION + ":8080: refused by the airgap agent"),
                    () -> assertThat(ReportLines.aboutExactly(REFUSED_DESTINATION + ":8080"))
                            .anyMatch(line -> line.contains("\"proxy\":\"" + proxy.hostAndPort() + "\"")),
                    () -> assertThat(proxy.connections()).as("refused before the proxy is reached").isZero());
        }
    }

    @Test
    void letsTheJdkClientReachAnAllowedDestinationThroughTheProxy() throws IOException, ReflectiveOperationException {
        assumeTrue(JdkHttpClient.present(), "the JDK's HTTP client is in JDK 11 and later");
        try (StandInProxy proxy = new StandInProxy()) {
            final Object client = JdkHttpClient.clientThrough(proxy.selector());
            assertThat(JdkHttpClient.statusOf(JdkHttpClient.send(client, ALLOWED_URL))).isEqualTo(200);
        }
    }
}
