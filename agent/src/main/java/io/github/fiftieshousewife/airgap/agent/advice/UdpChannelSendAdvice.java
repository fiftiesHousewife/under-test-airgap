package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.NetworkGate;
import java.net.SocketAddress;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code DatagramChannel.send(ByteBuffer, SocketAddress)}. */
public final class UdpChannelSendAdvice {

    private UdpChannelSendAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(1) final SocketAddress address) {
        NetworkGate.udp(address);
    }
}
