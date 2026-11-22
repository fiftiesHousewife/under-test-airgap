package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.DatagramPacket;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code DatagramSocket.send}. */
public final class UdpPacketAdvice {

    private UdpPacketAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final DatagramPacket packet) {
        Gate.udp(packet);
    }
}
