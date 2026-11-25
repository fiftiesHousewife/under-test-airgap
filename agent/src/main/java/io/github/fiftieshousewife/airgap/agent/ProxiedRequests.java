package io.github.fiftieshousewife.airgap.agent;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;

// Requests the JDK's own HTTP clients send through an HTTP proxy, whose destination is judged as well as the proxy (see
// the decision "Behind an HTTP proxy, the JDK's own HTTP clients are judged by their real destination as well as the
// proxy"). The JDK 11 client is read through its public API, by reflection, since the agent is built for Java 8.
final class ProxiedRequests {

    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    private ProxiedRequests() {
    }

    // The destination, if the proxy is an HTTP one; a direct connection is checked by the socket hooks instead.
    static Target ofUrlConnection(final String host, final int port, final Proxy proxy) {
        return host == null || proxy == null || proxy.type() != Proxy.Type.HTTP ? null
                : Target.ofProxiedRequest(host, port, text(proxy.address()));
    }

    // The JDK 11 client's request's destination, if the client's proxy selector picks an HTTP proxy for it.
    static Target ofJdkClientRequest(final Object client, final Object request) throws ReflectiveOperationException {
        // The client's own class loader: the agent's, the bootstrap one, can't see the java.net.http module.
        final ClassLoader clients = client.getClass().getClassLoader();
        final URI uri = (URI) Class.forName("java.net.http.HttpRequest", false, clients).getMethod("uri")
                .invoke(request);
        final ProxySelector selector = selectorOf(client, clients);
        if (uri == null || uri.getHost() == null || selector == null) {
            return null;
        }
        final List<Proxy> chosen = selector.select(uri);
        return chosen.isEmpty() ? null : ofUrlConnection(uri.getHost(), portOf(uri), chosen.get(0));
    }

    private static ProxySelector selectorOf(final Object client, final ClassLoader clients)
            throws ReflectiveOperationException {
        final Method proxy = Class.forName("java.net.http.HttpClient", false, clients).getMethod("proxy");
        final Optional<?> selector = (Optional<?>) proxy.invoke(client);
        return selector.isPresent() ? (ProxySelector) selector.get() : null;
    }

    private static int portOf(final URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return Ascii.equalsIgnoringCase("https", String.valueOf(uri.getScheme())) ? HTTPS_PORT : HTTP_PORT;
    }

    private static String text(final SocketAddress address) {
        if (!(address instanceof InetSocketAddress)) {
            return String.valueOf(address);
        }
        final InetSocketAddress internet = (InetSocketAddress) address;
        return internet.getHostString() + ":" + internet.getPort();
    }
}
