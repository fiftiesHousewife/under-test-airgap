package io.github.fiftieshousewife.airgap.agent.probes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Hook;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;

/**
 * The probes of the lookup, TCP and UDP hooks. Lookups and connects go to a name that can't resolve,
 * {@link #PROBE_HOST}, and reverse
 * lookups and packets to the machine itself, so each is harmless even if its hook is missing.
 */
@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET",
        justification = "The probes never connect: the hook refuses them, and their target can't resolve")
public final class NetworkProbes {

    public static final String PROBE_HOST = "airgap-self-check.invalid";
    private static final int DISCARD_PORT = 9;
    private static final CompletionHandler<Void, Object> HANDLER_THE_HOOK_NEVER_LETS_RUN =
            new CompletionHandler<Void, Object>() {
                @Override
                public void completed(final Void result, final Object attachment) {
                    // Never called: the probe's connect can't resolve its address.
                }

                @Override
                public void failed(final Throwable thrown, final Object attachment) {
                    // Never called: the probe's connect can't resolve its address.
                }
            };

    private NetworkProbes() {
    }

    /** @param probes where to add each lookup probe, by its hook */
    public static void addLookups(final Map<Hook, Probe> probes) {
        // Through getByName, so a JDK where getByName stopped reaching getAllByName fails the check.
        probes.put(Hook.HOST_LOOKUP_ALL, () -> InetAddress.getByName(PROBE_HOST));
        probes.put(Hook.HOST_LOOKUP_REVERSE, () -> InetAddress.getByAddress(new byte[] {127, 0, 0, 1}).getHostName());
    }

    /** @param probes where to add each TCP probe, by its hook */
    public static void addTcp(final Map<Hook, Probe> probes) {
        probes.put(Hook.TCP_SOCKET, () -> closing(new Socket(), socket -> socket.connect(nowhere(), 1)));
        probes.put(Hook.TCP_CHANNEL, () -> closing(SocketChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.TCP_SOCKET_ADAPTOR,
                () -> closing(SocketChannel.open(), channel -> channel.socket().connect(nowhere(), 1)));
        probes.put(Hook.TCP_REACHABILITY, NetworkProbes::pingTheMachineItself);
        probes.put(Hook.TCP_ASYNC,
                () -> closing(AsynchronousSocketChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.TCP_ASYNC_HANDLER, () -> closing(AsynchronousSocketChannel.open(),
                channel -> channel.connect(nowhere(), null, HANDLER_THE_HOOK_NEVER_LETS_RUN)));
    }

    /** @param probes where to add each UDP probe, by its hook */
    public static void addUdp(final Map<Hook, Probe> probes) {
        probes.put(Hook.UDP_SOCKET_CONNECT, () -> closing(new DatagramSocket(), socket -> socket.connect(nowhere())));
        probes.put(Hook.UDP_SOCKET_CONNECT_ADDRESS, () -> closing(new DatagramSocket(),
                socket -> socket.connect(InetAddress.getLoopbackAddress(), DISCARD_PORT)));
        probes.put(Hook.UDP_SOCKET_SEND, () -> closing(new DatagramSocket(), socket -> socket.send(loopbackPacket())));
        probes.put(Hook.UDP_MULTICAST_SEND, NetworkProbes::sendWithATimeToLive);
        probes.put(Hook.UDP_CHANNEL_CONNECT,
                () -> closing(DatagramChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.UDP_CHANNEL_SEND,
                () -> closing(DatagramChannel.open(), channel -> channel.send(ByteBuffer.allocate(1), nowhere())));
    }

    private static InetSocketAddress nowhere() {
        return InetSocketAddress.createUnresolved(PROBE_HOST, DISCARD_PORT);
    }

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "The probe needs only the call made")
    private static void pingTheMachineItself() throws IOException {
        InetAddress.getLoopbackAddress().isReachable(null, 0, 1);
    }

    // The deprecated MulticastSocket.send(packet, ttl), called by reflection as the API checks require.
    private static void sendWithATimeToLive() throws IOException, ReflectiveOperationException {
        try (MulticastSocket socket = new MulticastSocket()) {
            MulticastSocket.class.getMethod("send", DatagramPacket.class, byte.class)
                    .invoke(socket, loopbackPacket(), (byte) 1);
        }
    }

    private static DatagramPacket loopbackPacket() {
        return new DatagramPacket(new byte[1], 1, InetAddress.getLoopbackAddress(), DISCARD_PORT);
    }

    // A call on something that must be closed afterwards.
    private interface Use<T> {
        void on(T resource) throws IOException;
    }

    private static <T extends Closeable> void closing(final T resource, final Use<T> use) throws IOException {
        try (T opened = resource) {
            use.on(opened);
        }
    }
}
