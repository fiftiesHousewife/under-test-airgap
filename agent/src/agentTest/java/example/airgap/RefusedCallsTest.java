package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Every hooked kind of call, made for real with the agent attached, is refused at once with the exception an
 * unplugged machine would throw. Every target is either a name that can't resolve or an address reserved for
 * documentation, so nothing could leave the machine even if a hook failed; and every test has a short timeout, so a
 * refusal that didn't happen shows as a failure, not a slow pass.
 */
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = {"UNENCRYPTED_SOCKET", "UNENCRYPTED_SERVER_SOCKET"},
        justification = "Plain connections are what the agent must refuse; the one allowed is to the machine itself")
class RefusedCallsTest {

    private static final String UNRESOLVABLE = "db.prod.example.invalid";
    private static final String DOCUMENTATION_ADDRESS = "192.0.2.1";

    @Test
    void refusesHostLookups() {
        assertAll(
                () -> assertThatThrownBy(() -> InetAddress.getByName(UNRESOLVABLE))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> InetAddress.getAllByName(UNRESOLVABLE))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> new Socket(UNRESOLVABLE, 5432))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED));
    }

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
                        channel.connect(remote);
                    }
                }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesHttpThroughUrlConnections() {
        assertThatThrownBy(() -> {
            final HttpURLConnection connection = (HttpURLConnection)
                    new URL("http://" + DOCUMENTATION_ADDRESS + ":8080/orders").openConnection();
            connection.setConnectTimeout(1000);
            connection.connect();
        }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED);
    }

    @Test
    void refusesUdpPacketsAndConnects() {
        final InetSocketAddress remote = new InetSocketAddress(DOCUMENTATION_ADDRESS, 53);
        assertAll(
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.send(new DatagramPacket(new byte[1], 1, remote));
                    }
                }).isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.connect(remote.getAddress(), 53);
                    }
                }).isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (DatagramChannel channel = DatagramChannel.open()) {
                        channel.send(ByteBuffer.allocate(1), remote);
                    }
                }).isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesStartingPrograms() {
        assertAll(
                () -> assertThatThrownBy(() -> new ProcessBuilder("curl", "https://example.com").start())
                        .isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> Runtime.getRuntime().exec(new String[] {"sh", "-c", "echo"}))
                        .isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void allowsTheMachineItself() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket()) {
            client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), 1000);
            assertAll(
                    () -> assertThat(client.isConnected()).isTrue(),
                    () -> assertThat(InetAddress.getByName("localhost").isLoopbackAddress()).isTrue(),
                    () -> assertThat(InetAddress.getByName(DOCUMENTATION_ADDRESS).getHostAddress())
                            .isEqualTo(DOCUMENTATION_ADDRESS));
        }
    }

    @Test
    void allowsWhatTheSettingsAllowWithoutMakingTheCall() {
        assertAll(
                () -> assertThat(Gate.isAllowed("allowed.example.invalid", 8443)).isTrue(),
                () -> assertThat(Gate.isAllowed("allowed.example.invalid", 9999)).isFalse(),
                () -> assertThat(Gate.isAllowed(UNRESOLVABLE, 5432)).isFalse(),
                () -> assertThat(Gate.isAllowed("localhost", 5432)).isTrue());
    }
}
