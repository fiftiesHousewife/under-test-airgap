package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Calls to the machine itself go ahead with the agent attached: loopback, and the machine's own network addresses. */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = {"UNENCRYPTED_SOCKET", "UNENCRYPTED_SERVER_SOCKET"},
        justification = "The only connections made are to the machine itself")
class TheMachineItselfTest {

    @Test
    void allowsTheMachineItself() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket()) {
            client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), 1000);
            assertAll(
                    () -> assertThat(client.isConnected()).isTrue(),
                    () -> assertThat(InetAddress.getByName("localhost").isLoopbackAddress()).isTrue(),
                    () -> assertThat(InetAddress.getByName("192.0.2.1").getHostAddress())
                            .isEqualTo("192.0.2.1"));
        }
    }

    /** RMI and JMX hand out the machine's own network address for objects in the same JVM. */
    @Test
    void allowsTheMachinesOwnNetworkAddresses() throws IOException {
        final InetAddress own = ownNetworkAddress();
        assumeTrue(own != null, "this machine has no network address but loopback");
        try (ServerSocket server = new ServerSocket(0, 1, own);
             Socket client = new Socket();
             DatagramSocket udp = new DatagramSocket()) {
            client.connect(new InetSocketAddress(own, server.getLocalPort()), 1000);
            udp.send(new DatagramPacket(new byte[1], 1, own, server.getLocalPort()));
            assertThat(client.isConnected()).isTrue();
        }
    }

    private static InetAddress ownNetworkAddress() throws SocketException {
        for (final NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (final InetAddress address : Collections.list(network.getInetAddresses())) {
                if (network.isUp() && address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address;
                }
            }
        }
        return null;
    }
}
