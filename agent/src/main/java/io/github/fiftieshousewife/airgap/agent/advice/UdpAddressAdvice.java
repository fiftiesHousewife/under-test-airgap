package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.InetAddress;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code DatagramSocket.connect(InetAddress, int)}. */
public final class UdpAddressAdvice {

    private UdpAddressAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final InetAddress address, @Advice.Argument(1) final int port) {
        Gate.udp(address, port);
    }
}
