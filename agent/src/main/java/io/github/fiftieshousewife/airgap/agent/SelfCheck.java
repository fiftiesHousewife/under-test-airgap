package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Proves, at start, that every hook is in place: makes each hooked call with the gate in probing mode, where a hook
 * that is reached throws {@link Gate.Reached} before the call does anything. A hook that isn't reached means the JDK
 * has changed under it, and the agent refuses to start rather than guard partly.
 *
 * <p>Every probe is harmless even if its hook is missing: lookups and connects go to a name that can't resolve,
 * packets to the machine itself, and the program started doesn't exist.</p>
 */
@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET",
        justification = "The probes never connect: the hook refuses them, and their target can't resolve")
final class SelfCheck {

    static final String PROBE_HOST = "airgap-self-check.invalid";
    private static final Map<Hook, Probe> PROBES = buildProbes();

    private SelfCheck() {
    }

    /** A hooked call made only to see whether its hook is reached. */
    private interface Probe {
        void call() throws IOException;
    }

    /**
     * Probes every hook, including any the settings left out, so leaving one out proves this check works.
     *
     * @param installErrors classes that couldn't be rewritten, and why, to name in the message
     */
    static void run(final List<String> installErrors) {
        final List<String> missing = new ArrayList<>();
        for (final Hook hook : Hook.values()) {
            if (!reaches(probe(hook))) {
                missing.add(hook.id());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The airgap agent couldn't put its hooks in place for " + missing
                    + " on Java " + System.getProperty("java.version") + ", so it won't start: this JDK isn't supported"
                    + " yet. Nothing was run unguarded." + (installErrors.isEmpty() ? "" : " Why: " + installErrors));
        }
    }

    private static boolean reaches(final Probe probe) {
        Gate.setProbing(true);
        try {
            probe.call();
            return false;
        } catch (Gate.Reached reached) {
            return true;
        } catch (IOException | RuntimeException notReached) {
            return false;
        } finally {
            Gate.setProbing(false);
        }
    }

    private static InetSocketAddress nowhere() {
        return InetSocketAddress.createUnresolved(PROBE_HOST, 9);
    }

    private static Probe probe(final Hook hook) {
        return PROBES.get(hook);
    }

    private static Map<Hook, Probe> buildProbes() {
        final Map<Hook, Probe> probes = new EnumMap<>(Hook.class);
        probes.put(Hook.HOST_LOOKUP_ONE, () -> InetAddress.getByName(PROBE_HOST));
        probes.put(Hook.HOST_LOOKUP_ALL, () -> InetAddress.getAllByName(PROBE_HOST));
        probes.put(Hook.TCP_SOCKET, () -> closing(new Socket(), socket -> socket.connect(nowhere(), 1)));
        probes.put(Hook.TCP_CHANNEL, () -> closing(SocketChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.TCP_ASYNC,
                () -> closing(AsynchronousSocketChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.UDP_SOCKET_CONNECT, () -> closing(new DatagramSocket(), socket -> socket.connect(nowhere())));
        probes.put(Hook.UDP_SOCKET_CONNECT_ADDRESS,
                () -> closing(new DatagramSocket(), socket -> socket.connect(InetAddress.getLoopbackAddress(), 9)));
        probes.put(Hook.UDP_SOCKET_SEND, () -> closing(new DatagramSocket(), socket -> socket.send(loopbackPacket())));
        probes.put(Hook.UDP_CHANNEL_CONNECT,
                () -> closing(DatagramChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.UDP_CHANNEL_SEND,
                () -> closing(DatagramChannel.open(), channel -> channel.send(ByteBuffer.allocate(1), nowhere())));
        probes.put(Hook.PROCESS, () -> new ProcessBuilder("airgap-self-check-no-such-program").start());
        return probes;
    }

    private static DatagramPacket loopbackPacket() {
        return new DatagramPacket(new byte[1], 1, InetAddress.getLoopbackAddress(), 9);
    }

    /** A call on something that must be closed afterwards. */
    private interface Use<T> {
        void on(T resource) throws IOException;
    }

    private static <T extends Closeable> void closing(final T resource, final Use<T> use) throws IOException {
        try (T opened = resource) {
            use.on(opened);
        }
    }
}
