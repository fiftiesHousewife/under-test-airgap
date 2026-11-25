package io.github.fiftieshousewife.airgap.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Where the network hooks send their calls: host lookups, TCP and UDP, and JNDI names. Each is decided by {@link Gate},
 * as every risky call is. Loaded by the bootstrap class loader, like {@link Gate}, so its methods are public.
 */
public final class NetworkGate {

    private static final ThreadLocal<Boolean> OWN_NAME = new ThreadLocal<>();
    private static final int TCP_ECHO_PORT_ISREACHABLE_TRIES = 7;

    private NetworkGate() {
    }

    /** A host name is being looked up; {@code null} means the machine itself. */
    public static void lookup(final String host) {
        if (host != null && OWN_NAME.get() == null) {
            Gate.check(Kind.HOST_LOOKUP, Target.ofLookup(host));
        }
    }

    /** The machine is looking up its own name: lookups on this thread go ahead until it has. */
    public static void enterOwnName() {
        OWN_NAME.set(Boolean.TRUE);
    }

    /** The machine has its own name. */
    public static void leaveOwnName() {
        OWN_NAME.remove();
    }

    /**
     * Whether an address may be pinged: {@code isReachable} sends ICMP echo requests, or TCP ones to port 7.
     *
     * @param address the address
     * @return true if refused, so the check is skipped and answers that it can't be reached
     */
    public static boolean reachability(final InetAddress address) {
        return Gate.refusal(Kind.TCP, Target.ofAddress(address, TCP_ECHO_PORT_ISREACHABLE_TRIES)) != null;
    }

    /**
     * An address's name is being looked up. Refused, the caller gets the address back as its name, as offline.
     *
     * @param address the address
     * @return true if refused, so the lookup is skipped
     */
    public static boolean reverseLookup(final InetAddress address) {
        return address != null && Gate.refusal(Kind.HOST_LOOKUP, Target.ofReverseLookup(address)) != null;
    }

    /** A TCP connection is being made, to the address given. */
    public static void tcp(final SocketAddress address) {
        Gate.check(Kind.TCP, Target.ofAddress(address));
    }

    /**
     * @return what an asynchronous TCP connection to the address fails with, through its future or handler, if it's
     *     refused; or {@code null} if it may go ahead
     */
    public static Throwable tcpRefusal(final SocketAddress address) {
        return Gate.refusal(Kind.TCP, Target.ofAddress(address));
    }

    /** @return a future that has failed, with the failure given */
    public static Future<Void> failedFuture(final Throwable failure) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }

    /** A UDP socket is being connected, or a packet sent, to the address given. */
    public static void udp(final SocketAddress address) {
        Gate.check(Kind.UDP, Target.ofAddress(address));
    }

    /** A UDP socket is being connected to an address and port. */
    public static void udp(final InetAddress address, final int port) {
        final Throwable refusal = Gate.refusal(Kind.UDP, Target.ofAddress(address, port));
        if (refusal instanceof IOException) {
            // This connect declares no checked exception; from JDK 14 the JDK's own failure is this one.
            throw new UncheckedIOException((IOException) refusal);
        }
    }

    /** A UDP packet is being sent; one without an address goes where its socket is connected, already checked. */
    public static void udp(final DatagramPacket packet) {
        if (packet != null && packet.getAddress() != null) {
            Gate.check(Kind.UDP, Target.ofAddress(packet.getAddress(), packet.getPort()));
        }
    }

    /**
     * A JNDI name is being looked up, bound or listed. Only URLs whose provider reaches another machine are checked;
     * names inside the JVM, such as {@code java:comp/env}, are left alone.
     *
     * @param name the name, as written
     */
    public static void jndi(final String name) {
        if (name != null && Target.isNetworkJndiName(name)) {
            Gate.check(Kind.JNDI, Target.ofJndi(name));
        }
    }

    /** A JNDI context is being made from its settings, whose provider URL (a list, by spaces) names the directory. */
    public static void jndiProvider(final Map<?, ?> environment) {
        final Object urls = environment == null ? null : environment.get("java.naming.provider.url");
        if (urls instanceof String) {
            for (final String url : ((String) urls).trim().split("\\s+")) {
                jndi(url);
            }
        }
    }
}
