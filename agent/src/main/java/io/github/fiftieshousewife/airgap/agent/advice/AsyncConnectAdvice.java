package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Copied into {@code AsynchronousSocketChannel.connect(SocketAddress)}. A refused connect is skipped and returns a
 * future that has failed, as an unreachable address's does: the method declares no checked exception to throw.
 */
public final class AsyncConnectAdvice {

    private AsyncConnectAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static Throwable enter(@Advice.Argument(0) final SocketAddress address) {
        return Gate.tcpRefusal(address);
    }

    @Advice.OnMethodExit
    @SuppressWarnings("PMD.AvoidReassigningParameters")
    @SuppressFBWarnings(value = {"IP_PARAMETER_IS_DEAD_BUT_OVERWRITTEN", "DLS_DEAD_LOCAL_STORE"},
            justification = "Byte Buddy turns the assignment into the method's return value")
    static void exit(@Advice.Enter final Throwable refusal,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Future<Void> result) {
        if (refusal != null) {
            result = Gate.failedFuture(refusal);
        }
    }
}
