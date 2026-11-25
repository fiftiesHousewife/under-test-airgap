package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;

// Which requests through a proxy have their destination judged, and how it's named: only those through an HTTP proxy,
// named by host and port, with the proxy beside it.
class ProxiedRequestsTest {

    private static final InetSocketAddress PROXY = InetSocketAddress.createUnresolved("proxy.example.invalid", 3128);
    private static final Proxy HTTP_PROXY = new Proxy(Proxy.Type.HTTP, PROXY);

    private static Target jdkClientRequest(final HttpClient client, final String uri)
            throws ReflectiveOperationException {
        return ProxiedRequests.ofJdkClientRequest(client, HttpRequest.newBuilder(URI.create(uri)).build());
    }

    @Test
    void judgesTheDestinationOnlyBehindAnHttpProxy() {
        final Target behind = ProxiedRequests.ofUrlConnection("orders.example.com", 8080, HTTP_PROXY);
        assertAll(
                () -> assertThat(behind.text()).isEqualTo("orders.example.com:8080"),
                () -> assertThat(behind.getProxy()).isEqualTo("proxy.example.invalid:3128"),
                () -> assertThat(behind.isUnresolved()).as("the client never looks the destination up").isTrue(),
                () -> assertThat(ProxiedRequests.ofUrlConnection("orders.example.com", 80, Proxy.NO_PROXY)).isNull(),
                () -> assertThat(ProxiedRequests.ofUrlConnection("orders.example.com", 80,
                        new Proxy(Proxy.Type.SOCKS, PROXY))).as("a SOCKS proxy is reached by the socket").isNull(),
                () -> assertThat(ProxiedRequests.ofUrlConnection("orders.example.com", 80, null)).isNull(),
                () -> assertThat(ProxiedRequests.ofUrlConnection(null, 80, HTTP_PROXY)).isNull());
    }

    @Test
    void namesTheMachineItselfBehindAProxyAsLocal() {
        assertThat(ProxiedRequests.ofUrlConnection("localhost", 8080, HTTP_PROXY).isLocal()).isTrue();
    }

    @Test
    void readsTheJdkClientsRequestAndItsProxy() throws ReflectiveOperationException {
        final HttpClient proxied = HttpClient.newBuilder().proxy(ProxySelector.of(PROXY)).build();
        assertAll(
                () -> assertThat(jdkClientRequest(proxied, "https://orders.example.com/x").text())
                        .as("https's port when none is given").isEqualTo("orders.example.com:443"),
                () -> assertThat(jdkClientRequest(proxied, "http://orders.example.com/x").text())
                        .isEqualTo("orders.example.com:80"),
                () -> assertThat(jdkClientRequest(proxied, "http://orders.example.com:9090/x").text())
                        .isEqualTo("orders.example.com:9090"),
                () -> assertThat(jdkClientRequest(proxied, "http://orders.example.com/x").getProxy())
                        .isEqualTo("proxy.example.invalid:3128"),
                () -> assertThat(jdkClientRequest(HttpClient.newHttpClient(), "http://orders.example.com/x"))
                        .as("a client with no proxy connects directly").isNull());
    }
}
