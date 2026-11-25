package example.airgap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// An HTTP proxy on the machine itself, which the agent always allows: it answers every request it's sent with an
// empty 200, and counts the connections it accepts, so a test can tell whether a request reached it.
@SuppressFBWarnings(value = "UNENCRYPTED_SERVER_SOCKET",
        justification = "A stand-in HTTP proxy on the loopback address, for the tests' own requests")
final class StandInProxy implements AutoCloseable {

    private final ServerSocket server;
    private final AtomicInteger accepted = new AtomicInteger();

    StandInProxy() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        final Thread answering = new Thread(this::answerUntilClosed, "stand-in proxy");
        answering.setDaemon(true);
        answering.start();
    }

    InetSocketAddress address() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort());
    }

    String hostAndPort() {
        return address().getHostString() + ":" + server.getLocalPort();
    }

    // A proxy selector that picks this proxy for every request, as the JDK 9 ProxySelector.of does.
    ProxySelector selector() {
        return new Always(new Proxy(Proxy.Type.HTTP, address()));
    }

    private static final class Always extends ProxySelector {
        private final Proxy proxy;

        Always(final Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public List<Proxy> select(final URI uri) {
            return Collections.singletonList(proxy);
        }

        @Override
        public void connectFailed(final URI uri, final SocketAddress address, final IOException failure) {
            // The stand-in is always there; nothing to note.
        }
    }

    int connections() {
        return accepted.get();
    }

    private void answerUntilClosed() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                accepted.incrementAndGet();
                answer(client);
            } catch (IOException closed) {
                return;
            }
        }
    }

    private static void answer(final Socket client) throws IOException {
        final BufferedReader request = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
        // The request's line and headers, read so the reply follows them.
        String line = request.readLine();
        while (line != null && !line.isEmpty()) {
            line = request.readLine();
        }
        final OutputStream reply = client.getOutputStream();
        reply.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII));
        reply.flush();
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
