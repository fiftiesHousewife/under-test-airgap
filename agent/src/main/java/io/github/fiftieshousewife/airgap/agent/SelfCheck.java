package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import net.bytebuddy.description.type.TypeDescription;

/**
 * Proves, at start, that every hook is in place: makes each hooked call with the gate in probing mode, where a hook
 * that is reached throws {@link Gate.Reached} before the call does anything. A hook that isn't reached means the JDK
 * has changed under it, and the agent refuses to start rather than guard partly.
 *
 * <p>Every probe is harmless even if its hook is missing: lookups and connects go to a name that can't resolve,
 * reverse lookups and packets to the machine itself, the program started and the libraries loaded don't exist, and
 * the JNDI contexts have no provider to connect with. A few hooks can't be probed that way: ending the JVM (a missing
 * hook would end it), the machine asking its own name (which doesn't refuse), SCTP (not every platform supports it)
 * and the foreign function API (probing it sets off the JDK's warning about native access). Those are proved by
 * their methods being there to rewrite, in classes the other probes prove rewritten, or, for SCTP and the foreign
 * function API, by the agent's own tests.</p>
 */
@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET",
        justification = "The probes never connect: the hook refuses them, and their target can't resolve")
final class SelfCheck {

    static final String PROBE_HOST = "airgap-self-check.invalid";
    private static final String NO_SUCH_LIBRARY = "airgap-self-check-no-such-library";
    /** A handler for the asynchronous connect's probe, which the hook stops before it could be called. */
    private static final CompletionHandler<Void, Object> IGNORED = new CompletionHandler<Void, Object>() {
        @Override
        public void completed(final Void result, final Object attachment) {
            // Never called: the probe's connect can't resolve its address.
        }

        @Override
        public void failed(final Throwable thrown, final Object attachment) {
            // Never called: the probe's connect can't resolve its address.
        }
    };
    private static final Map<Hook, Probe> PROBES = buildProbes();

    private SelfCheck() {
    }

    /** A hooked call made only to see whether its hook is reached. */
    private interface Probe {
        void call() throws IOException, NamingException, ReflectiveOperationException;
    }

    /**
     * Probes every hook, including any the settings left out, so leaving one out proves this check works.
     *
     * @param installErrors classes that couldn't be rewritten, and why, to name in the message
     * @param without hooks the settings left out, which the exit hooks' check can't otherwise see
     */
    static void run(final List<String> installErrors, final Set<String> without) {
        final List<String> missing = new ArrayList<>();
        for (final Hook hook : Hook.values()) {
            if (!hook.appliesHere()) {
                continue;
            }
            final boolean inPlace = PROBES.containsKey(hook) ? reaches(hook, probe(hook))
                    : !without.contains(hook.id()) && isThereToRewrite(hook);
            if (!inPlace) {
                missing.add(hook.id());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The airgap agent couldn't put its hooks in place for " + missing
                    + " on Java " + System.getProperty("java.version") + ", so it won't start: this JDK isn't supported"
                    + " yet. Nothing was run unguarded." + (installErrors.isEmpty() ? "" : " Why: " + installErrors));
        }
    }

    private static boolean reaches(final Hook hook, final Probe probe) {
        Gate.setProbing(hook.kind());
        try {
            probe.call();
            return false;
        } catch (Gate.Reached reached) {
            // A JNDI probe whose own hook is missing could reach a network hook instead; that doesn't count.
            return reached.getKind() == hook.kind();
        } catch (InvocationTargetException wrapped) {
            final Throwable cause = wrapped.getCause();
            return cause instanceof Gate.Reached && ((Gate.Reached) cause).getKind() == hook.kind();
        } catch (IOException | NamingException | ReflectiveOperationException | RuntimeException
                | UnsatisfiedLinkError notReached) {
            return false;
        } finally {
            Gate.setProbing(null);
        }
    }

    /** For a hook with no harmless probe: whether its class has the methods it rewrites. */
    private static boolean isThereToRewrite(final Hook hook) {
        final Class<?> type = Hook.load(hook.getTypeName());
        return type != null && !TypeDescription.ForLoadedType.of(type).getDeclaredMethods()
                .filter(hook.getMethods()).isEmpty();
    }

    private static InetSocketAddress nowhere() {
        return InetSocketAddress.createUnresolved(PROBE_HOST, 9);
    }

    private static Probe probe(final Hook hook) {
        return PROBES.get(hook);
    }

    private static Map<Hook, Probe> buildProbes() {
        final Map<Hook, Probe> probes = new EnumMap<>(Hook.class);
        // Through getByName, so a JDK where getByName stopped reaching getAllByName fails the check.
        probes.put(Hook.HOST_LOOKUP_ALL, () -> InetAddress.getByName(PROBE_HOST));
        probes.put(Hook.HOST_LOOKUP_REVERSE, () -> InetAddress.getByAddress(new byte[] {127, 0, 0, 1}).getHostName());
        probes.put(Hook.TCP_SOCKET, () -> closing(new Socket(), socket -> socket.connect(nowhere(), 1)));
        probes.put(Hook.TCP_CHANNEL, () -> closing(SocketChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.TCP_SOCKET_ADAPTOR,
                () -> closing(SocketChannel.open(), channel -> channel.socket().connect(nowhere(), 1)));
        probes.put(Hook.TCP_REACHABILITY, SelfCheck::pingTheMachineItself);
        probes.put(Hook.TCP_ASYNC,
                () -> closing(AsynchronousSocketChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.TCP_ASYNC_HANDLER,
                () -> closing(AsynchronousSocketChannel.open(), channel -> channel.connect(nowhere(), null, IGNORED)));
        probes.put(Hook.UDP_SOCKET_CONNECT, () -> closing(new DatagramSocket(), socket -> socket.connect(nowhere())));
        probes.put(Hook.UDP_SOCKET_CONNECT_ADDRESS,
                () -> closing(new DatagramSocket(), socket -> socket.connect(InetAddress.getLoopbackAddress(), 9)));
        probes.put(Hook.UDP_SOCKET_SEND, () -> closing(new DatagramSocket(), socket -> socket.send(loopbackPacket())));
        probes.put(Hook.UDP_MULTICAST_SEND, SelfCheck::sendWithATimeToLive);
        probes.put(Hook.UDP_CHANNEL_CONNECT,
                () -> closing(DatagramChannel.open(), channel -> channel.connect(nowhere())));
        probes.put(Hook.UDP_CHANNEL_SEND,
                () -> closing(DatagramChannel.open(), channel -> channel.send(ByteBuffer.allocate(1), nowhere())));
        probes.put(Hook.PROCESS, () -> new ProcessBuilder("airgap-self-check-no-such-program").start());
        probes.put(Hook.JNDI_NAME, () -> new ProbeContext().lookup("ldap://" + PROBE_HOST + "/o=probe"));
        probes.put(Hook.JNDI_COMPOSITE_NAME,
                () -> new ProbeContext().lookup(new CompositeName("ldap://" + PROBE_HOST + "/o=probe")));
        probes.put(Hook.JNDI_PROVIDER, () -> new ProbeContext().makeTheDefaultContext());
        probes.put(Hook.NATIVE_LIBRARY_BY_NAME, () -> System.loadLibrary(NO_SUCH_LIBRARY));
        probes.put(Hook.NATIVE_LIBRARY_BY_PATH, () -> System.load(noSuchLibraryFile()));
        probes.put(Hook.NATIVE_LIBRARY_RUNTIME_BY_NAME, () -> Runtime.getRuntime().loadLibrary(NO_SUCH_LIBRARY));
        probes.put(Hook.NATIVE_LIBRARY_RUNTIME_BY_PATH, () -> Runtime.getRuntime().load(noSuchLibraryFile()));
        return probes;
    }

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED", justification = "The probe needs only the call made")
    private static void pingTheMachineItself() throws IOException {
        InetAddress.getLoopbackAddress().isReachable(null, 0, 1);
    }

    private static String noSuchLibraryFile() {
        return new File(System.getProperty("java.io.tmpdir"), NO_SUCH_LIBRARY).getAbsolutePath();
    }

    /** The deprecated {@code MulticastSocket.send(packet, ttl)}, called by reflection as the API checks require. */
    private static void sendWithATimeToLive() throws IOException, ReflectiveOperationException {
        try (MulticastSocket socket = new MulticastSocket()) {
            MulticastSocket.class.getMethod("send", DatagramPacket.class, byte.class)
                    .invoke(socket, loopbackPacket(), (byte) 1);
        }
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

    /**
     * A JNDI context with no settings read and no provider: a lookup of a URL fails without connecting, and making
     * its default context fails for want of a provider, if the hooks don't stop them first.
     */
    @SuppressWarnings("PMD.ReplaceHashtableWithMap") // JNDI's own type
    private static final class ProbeContext extends InitialContext {

        ProbeContext() throws NamingException {
            super(true);
        }

        void makeTheDefaultContext() throws NamingException {
            final Hashtable<Object, Object> environment = new Hashtable<>();
            environment.put(Context.PROVIDER_URL, "ldap://" + PROBE_HOST + ":389/o=probe");
            myProps = environment;
            getDefaultInitCtx();
        }
    }
}
