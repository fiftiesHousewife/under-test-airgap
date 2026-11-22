package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;

/** Copied into the start of every blocking TCP connect: sockets and socket channels. */
public final class TcpAdvice {

    private TcpAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final SocketAddress address) {
        Gate.tcp(address);
    }
}
