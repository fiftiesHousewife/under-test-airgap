package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import javax.naming.Name;
import net.bytebuddy.asm.Advice;

/** Copied into the start of {@code InitialContext.getURLOrDefaultInitCtx(Name)}, for lookups by {@link Name}. */
public final class JndiCompositeNameAdvice {

    private JndiCompositeNameAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(0) final Name name) {
        Gate.jndi(name == null ? null : name.toString());
    }
}
