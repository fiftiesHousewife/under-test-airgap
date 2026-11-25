package io.github.fiftieshousewife.airgap.agent;

import java.util.List;

/**
 * Where every hook sends its call. The hooks are copied into the JDK's own methods, so this class is loaded by the
 * bootstrap class loader and its methods are public. Each check is ignored while the agent itself is deciding (so its
 * own work can't trigger a check), and before the gate is open.
 *
 * <p>The network hooks go through {@link NetworkGate}, which decides here. The JUnit listener and extension (a separate
 * jar) call {@link #enterTest}, {@link #leaveTest},
 * {@link #allowForThread} and {@link #isAllowed} by reflection, so a project without the agent attached just runs.</p>
 */
public final class Gate {

    static final ThreadLocal<Boolean> DECIDING = new ThreadLocal<>();
    private static final ThreadLocal<Kind> PROBING = new ThreadLocal<>();
    private static volatile Decider decider;

    private Gate() {
    }

    /** Thrown, only while the self-check probes a hook, to show the hook was reached, and which kind of hook. */
    public static final class Reached extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Kind kind;

        /** @param kind the kind of hook reached */
        public Reached(final Kind kind) {
            super("airgap agent self-check: hook reached", null, false, false);
            this.kind = kind;
        }

        /** @return the kind of hook reached */
        public Kind getKind() {
            return kind;
        }
    }

    // Returns the decider, or null before the gate is open
    static Decider currentDecider() {
        return decider;
    }

    static void open(final Decider opened) {
        decider = opened;
    }

    // Puts this thread in probing mode, or takes it out.
    //
    // kind: the kind of hook being probed, or null to stop probing
    static void setProbing(final Kind kind) {
        PROBING.set(kind);
    }

    /** A program is being started, with the command and arguments given. */
    public static void process(final List<String> command) {
        check(Kind.PROCESS, Target.ofCommand(command));
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

    /** A test ({@code class.method}), or a test class's set-up, starts on this thread, inside any running there. */
    public static void enterTest(final String test) {
        RunningTests.enter(test);
    }

    /** A JUnit 4 test starts on this thread, as JUnit 4's notifier reports it, by its description: method(class). */
    public static void enterJUnit4Test(final String description) {
        RunningTests.enter(JUnit4Names.testName(description));
    }

    /** @return the test running on this thread, or {@code null} */
    public static String currentTest() {
        return RunningTests.current();
    }

    /** The test or test class on this thread has finished, here and in every thread it started. */
    public static void leaveTest() {
        RunningTests.leave();
    }

    /** Adds allowlist entries (host, host:port, process:name) for the rest of the test, on this and its threads. */
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

    // While probing, stops the call; a library, exit or file write met probing another kind is the JDK's own.
    static void stopIfProbing(final Kind kind) {
        final Kind probing = PROBING.get();
        if (probing != null && (probing == kind || !kind.isTheJdksOwnWork())) {
            throw new Reached(kind);
        }
    }

    static void check(final Kind kind, final Target target) {
        final Throwable refusal = refusal(kind, target);
        if (refusal != null) {
            Gate.<RuntimeException>sneakyThrow(refusal);
        }
    }

    // Returns what to throw if the call is refused, or null if it may go ahead
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
