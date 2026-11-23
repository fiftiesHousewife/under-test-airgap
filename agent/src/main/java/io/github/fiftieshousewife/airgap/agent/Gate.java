package io.github.fiftieshousewife.airgap.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Where every hook sends its call. The hooks are copied into the JDK's own methods, so this class is loaded by the
 * bootstrap class loader and its methods are public. Each check is ignored while the agent itself is deciding (so its
 * own work can't trigger a check), and before the gate is open.
 *
 * <p>The JUnit listener and extension (a separate jar) call {@link #enterTest}, {@link #leaveTest},
 * {@link #allowForThread} and {@link #isAllowed} by reflection, so a project without the agent attached just runs.</p>
 */
public final class Gate {

    static final ThreadLocal<Boolean> DECIDING = new ThreadLocal<>();
    private static final ThreadLocal<Kind> PROBING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> OWN_NAME = new ThreadLocal<>();
    /** The echo port {@code isReachable} tries over TCP when it can't send ICMP. */
    private static final int ECHO_PORT = 7;
    private static volatile Decider decider;

    private Gate() {
    }

    /** Thrown, only while the self-check probes a hook, to show the hook was reached, and which kind of hook. */
    public static final class Reached extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Kind kind;

        Reached(final Kind kind) {
            super("airgap agent self-check: hook reached", null, false, false);
            this.kind = kind;
        }

        /** @return the kind of hook reached */
        Kind getKind() {
            return kind;
        }
    }

    /** @return the decider, or {@code null} before the gate is open */
    static Decider currentDecider() {
        return decider;
    }

    static void open(final Decider opened) {
        decider = opened;
    }

    /**
     * Puts this thread in probing mode, or takes it out.
     *
     * @param kind the kind of hook being probed, or {@code null} to stop probing
     */
    static void setProbing(final Kind kind) {
        PROBING.set(kind);
    }

    /**
     * A host name is being looked up.
     *
     * @param host the name; {@code null} means the machine itself
     */
    public static void lookup(final String host) {
        if (host != null && OWN_NAME.get() == null) {
            check(Kind.HOST_LOOKUP, Target.ofLookup(host));
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
        return refusal(Kind.TCP, Target.ofAddress(address, ECHO_PORT)) != null;
    }

    /**
     * An address's name is being looked up. Refused, the caller gets the address back as its name, as offline.
     *
     * @param address the address
     * @return true if refused, so the lookup is skipped
     */
    public static boolean reverseLookup(final InetAddress address) {
        return address != null && refusal(Kind.HOST_LOOKUP, Target.ofReverseLookup(address)) != null;
    }

    /**
     * A TCP connection is being made.
     *
     * @param address where to
     */
    public static void tcp(final SocketAddress address) {
        check(Kind.TCP, Target.ofAddress(address));
    }

    /**
     * An asynchronous TCP connection is being made, which reports its failure through a future or handler.
     *
     * @param address where to
     * @return what it fails with if refused, or {@code null} if it may go ahead
     */
    public static Throwable tcpRefusal(final SocketAddress address) {
        return refusal(Kind.TCP, Target.ofAddress(address));
    }

    /**
     * A future that has failed.
     *
     * @param failure why
     * @return the future
     */
    public static Future<Void> failedFuture(final Throwable failure) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }

    /**
     * A UDP socket is being connected, or a packet sent.
     *
     * @param address where to
     */
    public static void udp(final SocketAddress address) {
        check(Kind.UDP, Target.ofAddress(address));
    }

    /**
     * A UDP socket is being connected to an address and port.
     *
     * @param address the address
     * @param port the port
     */
    public static void udp(final InetAddress address, final int port) {
        final Throwable refusal = refusal(Kind.UDP, Target.ofAddress(address, port));
        if (refusal instanceof IOException) {
            // This connect declares no checked exception; from JDK 14 the JDK's own failure is this one.
            throw new UncheckedIOException((IOException) refusal);
        }
    }

    /**
     * A UDP packet is being sent.
     *
     * @param packet the packet; one without an address goes where its socket is connected, already checked
     */
    public static void udp(final DatagramPacket packet) {
        if (packet != null && packet.getAddress() != null) {
            check(Kind.UDP, Target.ofAddress(packet.getAddress(), packet.getPort()));
        }
    }

    /**
     * A program is being started.
     *
     * @param command the command and its arguments
     */
    public static void process(final List<String> command) {
        check(Kind.PROCESS, Target.ofCommand(command));
    }

    /**
     * A JNDI name is being looked up, bound or listed. Only URLs whose provider reaches another machine are checked;
     * names inside the JVM, such as {@code java:comp/env}, are left alone.
     *
     * @param name the name, as written
     */
    public static void jndi(final String name) {
        if (name != null && Target.isNetworkJndiName(name)) {
            check(Kind.JNDI, Target.ofJndi(name));
        }
    }

    /**
     * A JNDI context is being made from its settings, whose provider URL (a space-separated list) names the directory.
     *
     * @param environment the context's settings, or {@code null}
     */
    public static void jndiProvider(final Map<?, ?> environment) {
        final Object urls = environment == null ? null : environment.get("java.naming.provider.url");
        if (urls instanceof String) {
            for (final String url : ((String) urls).trim().split("\\s+")) {
                jndi(url);
            }
        }
    }

    /**
     * The JVM is being ended. Checked only while a test is running on this thread, so test runners and build tools
     * end their JVMs as usual.
     *
     * @param method {@code exit} or {@code halt}
     * @param status the status
     */
    public static void exit(final String method, final int status) {
        stopIfProbing(Kind.EXIT);
        if (currentTest() != null) {
            check(Kind.EXIT, Target.ofExit(method, status));
        }
    }

    /**
     * A test, or a test class's set-up, is starting on this thread, inside any test class already running there.
     *
     * @param test the test, as {@code class.method}, or the test class
     */
    public static void enterTest(final String test) {
        RunningTests.enter(test);
    }

    /** @return the test running on this thread, or {@code null} */
    public static String currentTest() {
        return RunningTests.current();
    }

    /** The test or test class on this thread has finished, here and in every thread it started. */
    public static void leaveTest() {
        RunningTests.leave();
    }

    /**
     * Adds stand-ins for the rest of the running test or test class, on this thread and threads it starts.
     *
     * @param targets entries as an allowlist takes them: {@code host}, {@code host:port} or {@code process:name}
     */
    public static void allowForThread(final String... targets) {
        RunningTests.allow(targets);
    }

    /**
     * Whether a host and port would be allowed on this thread now, without making any call.
     *
     * @param host the host
     * @param port the port
     * @return true if the settings or the running test allow it, or it's the machine itself
     */
    public static boolean isAllowed(final String host, final int port) {
        final Target target = Target.ofAddress(java.net.InetSocketAddress.createUnresolved(host, port));
        final Decider current = decider;
        return target.isLocal() || current != null && current.allows(Kind.TCP, target, RunningTests.context());
    }

    /**
     * While probing, stops the call before it does anything. A native library or an exit met while probing another
     * kind of hook is the JDK's own work (loading a library a probed class needs), so it isn't stopped.
     */
    static void stopIfProbing(final Kind kind) {
        final Kind probing = PROBING.get();
        if (probing != null && (probing == kind || kind != Kind.NATIVE_LIBRARY && kind != Kind.EXIT)) {
            throw new Reached(kind);
        }
    }

    static void check(final Kind kind, final Target target) {
        final Throwable refusal = refusal(kind, target);
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(refusal);
        }
    }

    /** @return what to throw if the call is refused, or {@code null} if it may go ahead */
    static Throwable refusal(final Kind kind, final Target target) {
        stopIfProbing(kind);
        final Decider current = decider;
        if (current == null || DECIDING.get() != null) {
            return null;
        }
        DECIDING.set(Boolean.TRUE);
        try {
            return current.decide(kind, target, RunningTests.context());
        } finally {
            DECIDING.remove();
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable> void sneakyThrow(final Throwable thrown) throws T {
        throw (T) thrown;
    }
}
