package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into {@code InetAddress.getLocalHost}: while the machine looks up its own name, which is local, the lookup
 * hook lets it through.
 */
public final class OwnNameAdvice {

    private OwnNameAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter() {
        Gate.enterOwnName();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void exit() {
        Gate.leaveOwnName();
    }
}
