package io.github.fiftieshousewife.airgap.agent;

import java.io.IOException;
import java.net.ConnectException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.UnknownHostException;
import javax.naming.CommunicationException;

/** The exception an unplugged machine would throw for each kind of call, saying the agent refused it. */
public final class Refusals {

    /** The words every refusal ends with, so people and tools can tell a refusal from a real failure. */
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
        switch (kind) {
            case HOST_LOOKUP:
                return new UnknownHostException(what);
            case TCP:
                return target.isUnresolved() ? new UnknownHostException(what) : new ConnectException(what);
            case UDP:
                // The JDK's own refusal of a UDP address nobody looked up.
                return target.isUnresolved() ? new SocketException(what) : new PortUnreachableException(what);
            case JNDI:
                return new CommunicationException(what);
            case NATIVE_LIBRARY:
                return new UnsatisfiedLinkError(what);
            case EXIT:
                return new SecurityException(target.text() + ": a test may not end the JVM; " + REFUSED + detail);
            default:
                return new IOException("Cannot run program \"" + target.text() + "\": " + REFUSED + detail);
        }
    }
}
