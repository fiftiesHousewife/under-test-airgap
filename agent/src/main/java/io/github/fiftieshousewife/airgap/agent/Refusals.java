package io.github.fiftieshousewife.airgap.agent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.UnknownHostException;
import javax.naming.CommunicationException;

/**
 * The exception an unplugged machine would throw for each kind of call, saying the agent refused it. Every refusal ends
 * with {@link #REFUSED}, so people and tools can tell a refusal from a real failure.
 */
public final class Refusals {

    public static final String REFUSED = "refused by the airgap agent";

    private Refusals() {
    }

    /**
     * The exception for a refused call. A TCP connection to an address that was never looked up fails as the
     * lookup would, with {@link UnknownHostException}; a UDP one as the JDK refuses it, with {@link SocketException}.
     *
     * @param kind the kind of call
     * @param target what it tried to reach
     * @return the exception to throw
     */
    public static Throwable forCall(final Kind kind, final Target target) {
        return forCall(kind, target, "");
    }

    /**
     * The exception for a call refused because it couldn't be written to the report.
     *
     * @param kind the kind of call
     * @param target what it tried to reach
     * @param why why the report couldn't be written
     * @return the exception to throw
     */
    public static Throwable forUnreportedCall(final Kind kind, final Target target, final IOException why) {
        return forCall(kind, target, ", which couldn't write it to its report (" + why + ")");
    }

    private static Throwable forCall(final Kind kind, final Target target, final String detail) {
        final String what = target.text() + ": " + REFUSED + detail;
        if (kind.isNetwork()) {
            return forNetwork(kind, target, what);
        }
        if (kind == Kind.JNDI) {
            return new CommunicationException(what);
        }
        return kind == Kind.NATIVE_LIBRARY ? new UnsatisfiedLinkError(what) : forOther(kind, target, detail);
    }

    // A lookup, or a TCP or UDP call; UDP to an address nobody looked up fails as the JDK's own refusal does.
    private static Throwable forNetwork(final Kind kind, final Target target, final String what) {
        if (kind == Kind.UDP) {
            return target.isUnresolved() ? new SocketException(what) : new PortUnreachableException(what);
        }
        return kind == Kind.TCP && !target.isUnresolved() ? new ConnectException(what) : new UnknownHostException(what);
    }

    // Calls on this machine: a file write, an exit, or a program started.
    private static Throwable forOther(final Kind kind, final Target target, final String detail) {
        if (kind == Kind.FILE_WRITE) {
            // As the JDK words an unwritable file: the path, then why in brackets.
            return new FileNotFoundException(target.text() + " (" + REFUSED + detail + ")");
        }
        if (kind == Kind.EXIT) {
            return new SecurityException(target.text() + ": a test may not end the JVM; " + REFUSED + detail);
        }
        return new IOException("Cannot run program \"" + target.text() + "\": " + REFUSED + detail);
    }
}
