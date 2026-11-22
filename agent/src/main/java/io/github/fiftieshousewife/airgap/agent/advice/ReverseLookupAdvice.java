package io.github.fiftieshousewife.airgap.agent.advice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.InetAddress;
import net.bytebuddy.asm.Advice;

/**
 * Copied into {@code InetAddress.getHostFromNameService}, which {@code getHostName} and {@code getCanonicalHostName}
 * reach. A refused lookup skips the method and gives the address back as its name, as the JDK does offline.
 */
public final class ReverseLookupAdvice {

    private ReverseLookupAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(@Advice.Argument(0) final InetAddress address) {
        return Gate.reverseLookup(address);
    }

    @Advice.OnMethodExit
    @SuppressWarnings("PMD.AvoidReassigningParameters")
    @SuppressFBWarnings(value = {"IP_PARAMETER_IS_DEAD_BUT_OVERWRITTEN", "DLS_DEAD_LOCAL_STORE"},
            justification = "Byte Buddy turns the assignment into the method's return value")
    static void exit(@Advice.Enter final boolean refused, @Advice.Argument(0) final InetAddress address,
            @Advice.Return(readOnly = false) String name) {
        if (refused) {
            name = address.getHostAddress();
        }
    }
}
