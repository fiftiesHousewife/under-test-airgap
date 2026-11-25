package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.NetworkGate;
import java.net.InetAddress;
import net.bytebuddy.asm.Advice;

/**
 * Copied into {@code InetAddress.isReachable}. A refused check is skipped and answers that the address can't be
 * reached, as it can't from a machine without a network.
 */
public final class ReachabilityAdvice {

    private ReachabilityAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(@Advice.This final InetAddress address) {
        return NetworkGate.reachability(address);
    }

    @Advice.OnMethodExit
    @SuppressWarnings("PMD.AvoidReassigningParameters")
    @SuppressFBWarnings(value = {"IP_PARAMETER_IS_DEAD_BUT_OVERWRITTEN", "UC_USELESS_VOID_METHOD"},
            justification = "Byte Buddy turns the assignment into the method's return value")
    static void exit(@Advice.Enter final boolean refused, @Advice.Return(readOnly = false) boolean reachable) {
        if (refused) {
            reachable = false;
        }
    }
}
