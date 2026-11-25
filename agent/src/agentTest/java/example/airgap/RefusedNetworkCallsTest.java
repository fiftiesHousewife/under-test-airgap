package example.airgap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Connections to other machines, over TCP, HTTP and UDP, made for real with the agent attached, are refused at once
// with the exception an unplugged machine would throw. Every target is an address reserved for documentation or a
// name that can't resolve, so nothing could leave the machine even if a hook failed.
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET", justification = "Plain connections are what the agent must refuse")
class RefusedNetworkCallsTest {

    private static final String DOCUMENTATION_ADDRESS = "192.0.2.1";
    private static final InetSocketAddress DNS_PORT = new InetSocketAddress(DOCUMENTATION_ADDRESS, 53);

    @Test
    void refusesTcpThroughEveryRoute() {
        final InetSocketAddress remote = new InetSocketAddress(DOCUMENTATION_ADDRESS, 5432);
        assertAll(
                () -> assertThatThrownBy(() -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(remote, 1000);
                    }
                }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> SocketChannel.open(remote).close())
                        .isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
                        channel.connect(remote).get(5, TimeUnit.SECONDS);
                    }
                }).as("a failed future, as connect declares no checked exception")
                        .isInstanceOf(ExecutionException.class).cause()
                        .isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesHttpThroughUrlConnections() {
        assertThatThrownBy(() -> {
            final HttpURLConnection connection = (HttpURLConnection)
                    URI.create("http://" + DOCUMENTATION_ADDRESS + ":8080/orders").toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.connect();
        }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED);
    }

    @Test
    void refusesUdpPackets() {
        assertAll(
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.send(new DatagramPacket(new byte[1], 1, DNS_PORT));
                    }
                }).isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (DatagramChannel channel = DatagramChannel.open()) {
                        channel.send(ByteBuffer.allocate(1), DNS_PORT);
                    }
                }).isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesUdpConnects() {
        assertAll(
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.connect(DNS_PORT.getAddress(), 53);
                    }
                }).as("unchecked, as this connect declares no checked exception")
                        .isInstanceOf(UncheckedIOException.class).cause()
                        .isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.connect(InetSocketAddress.createUnresolved("db.prod.example.invalid", 53));
                    }
                }).as("as the JDK refuses an address nobody looked up")
                        .isExactlyInstanceOf(SocketException.class).hasMessageContaining(Refusals.REFUSED));
    }

    // The old multicast send with a time-to-live: deprecated, so called by reflection, but in every supported JDK.
    @Test
    void refusesMulticastPacketsSentWithATimeToLive() throws IOException, NoSuchMethodException {
        final Method send = MulticastSocket.class.getMethod("send", DatagramPacket.class, byte.class);
        try (MulticastSocket socket = new MulticastSocket()) {
            final DatagramPacket packet = new DatagramPacket(new byte[1], 1, DNS_PORT);
            assertThatThrownBy(() -> send.invoke(socket, packet, (byte) 1))
                    .isInstanceOf(InvocationTargetException.class).cause()
                    .isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED);
        }
    }
}
