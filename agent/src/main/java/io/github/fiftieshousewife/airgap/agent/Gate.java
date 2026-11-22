package io.github.fiftieshousewife.airgap.agent;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.List;

/**
 * Where every hook sends its call. The hooks are copied into the JDK's own methods, so this class is loaded by the
 * bootstrap class loader and its methods are public. Each check is ignored while the agent itself is deciding (so its
 * own work can't trigger a check), and before the gate is open.
 *
 * <p>The JUnit listener and extension (a separate jar) call {@link #enterTest}, {@link #leaveTest},
 * {@link #allowForThread} and {@link #isAllowed} by reflection, so a project without the agent attached just runs.</p>
 */
public final class Gate {

    private static final ThreadLocal<Boolean> DECIDING = new ThreadLocal<>();
    private static final ThreadLocal<Kind> PROBING = new ThreadLocal<>();
    private static final InheritableThreadLocal<String> TEST = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<Allowlist> ALLOWANCES = new InheritableThreadLocal<>();
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
        if (host != null) {
            check(Kind.HOST_LOOKUP, Target.ofLookup(host));
        }
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
        check(Kind.UDP, Target.ofAddress(address, port));
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
     * A native library is being loaded. The JDK's own (asked for by a JDK class) and the build tool's own go ahead
     * unchecked.
     *
     * @param library the library's name or path
     */
    public static void nativeLibrary(final String library) {
        stopIfProbing(Kind.NATIVE_LIBRARY);
        final Decider current = decider;
        if (current == null || DECIDING.get() != null) {
            return;
        }
        final String asking = LibraryLoads.askingClass(new Throwable().getStackTrace());
        if (LibraryLoads.isJdks(asking) || current.isBuildToolsOwn(asking)) {
            return;
        }
        check(Kind.NATIVE_LIBRARY, Target.ofLibrary(library));
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
        if (TEST.get() != null) {
            check(Kind.EXIT, Target.ofExit(method, status));
        }
    }

    /**
     * A test is starting on this thread.
     *
     * @param test the test, as {@code class.method}
     */
    public static void enterTest(final String test) {
        TEST.set(test);
    }

    /** @return the test running on this thread, or {@code null} */
    public static String currentTest() {
        return TEST.get();
    }

    /** The test on this thread has finished. */
    public static void leaveTest() {
        TEST.remove();
        ALLOWANCES.remove();
    }

    /**
     * Allows stand-ins for the rest of the running test, on this thread and threads it starts.
     *
     * @param targets entries as an allowlist takes them: {@code host}, {@code host:port} or {@code process:name}
     */
    public static void allowForThread(final String... targets) {
        ALLOWANCES.set(new Allowlist(Arrays.asList(targets)));
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
        return target.isLocal() || current != null && current.allows(Kind.TCP, target, context());
    }

    private static CallContext context() {
        final Allowlist allowances = ALLOWANCES.get();
        return new CallContext(TEST.get(),
                allowances == null ? new Allowlist(Collections.<String>emptyList()) : allowances);
    }

    /**
     * While probing, stops the call before it does anything. A native library or an exit met while probing another
     * kind of hook is the JDK's own work (loading a library a probed class needs), so it isn't stopped.
     */
    private static void stopIfProbing(final Kind kind) {
        final Kind probing = PROBING.get();
        if (probing != null && (probing == kind || kind != Kind.NATIVE_LIBRARY && kind != Kind.EXIT)) {
            throw new Reached(kind);
        }
    }

    private static void check(final Kind kind, final Target target) {
        stopIfProbing(kind);
        final Decider current = decider;
        if (current == null || DECIDING.get() != null) {
            return;
        }
        final Throwable refusal;
        DECIDING.set(Boolean.TRUE);
        try {
            refusal = current.decide(kind, target, context());
        } finally {
            DECIDING.remove();
        }
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(refusal);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(final Throwable thrown) throws T {
        throw (T) thrown;
    }
}
