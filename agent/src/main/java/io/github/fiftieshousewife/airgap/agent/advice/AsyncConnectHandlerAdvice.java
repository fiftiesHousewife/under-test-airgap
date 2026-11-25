package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.NetworkGate;
import java.net.SocketAddress;
import java.nio.channels.CompletionHandler;
import net.bytebuddy.asm.Advice;

/**
 * Copied into {@code AsynchronousSocketChannel.connect(SocketAddress, attachment, handler)}. A refused connect is
 * skipped and its handler told it failed, as for an unreachable address, so nothing is left waiting.
 */
public final class AsyncConnectHandlerAdvice {

    private AsyncConnectHandlerAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static Throwable enter(@Advice.Argument(0) final SocketAddress address) {
        return NetworkGate.tcpRefusal(address);
    }

    @Advice.OnMethodExit
    static void exit(@Advice.Enter final Throwable refusal, @Advice.Argument(1) final Object attachment,
            @Advice.Argument(2) final CompletionHandler<Void, Object> handler) {
        if (refusal != null) {
            handler.failed(refusal, attachment);
        }
    }
}
