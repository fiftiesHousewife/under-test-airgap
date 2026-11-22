package io.github.fiftieshousewife.airgap.agent;

import java.io.IOException;
import java.net.ConnectException;
import java.net.PortUnreachableException;
import java.net.UnknownHostException;

/** The exception an unplugged machine would throw for each kind of call, saying the agent refused it. */
public final class Refusals {

    /** The words every refusal ends with, so people and tools can tell a refusal from a real failure. */
    public static final String REFUSED = "refused by the airgap agent";

    private Refusals() {
    }

    /**
     * The exception for a refused call. A connection to an address that was never looked up fails as the lookup
     * would, with {@link UnknownHostException}.
     *
     * @param kind the kind of call
     * @param target what it tried to reach
     * @return the exception to throw
     */
    public static IOException forCall(final Kind kind, final Target target) {
        final String what = target.text();
        if (kind == Kind.HOST_LOOKUP || target.isUnresolved() && kind != Kind.PROCESS) {
            return new UnknownHostException(what + ": " + REFUSED);
        }
        if (kind == Kind.TCP) {
            return new ConnectException(what + ": " + REFUSED);
        }
        if (kind == Kind.UDP) {
            return new PortUnreachableException(what + ": " + REFUSED);
        }
        return new IOException("Cannot run program \"" + what + "\": " + REFUSED);
    }
}
