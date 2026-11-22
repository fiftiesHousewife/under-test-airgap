package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;

/** Copied into the start of UDP connects that take a socket address. */
public final class UdpAdvice {

    private UdpAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final SocketAddress address) {
        Gate.udp(address);
    }
}
