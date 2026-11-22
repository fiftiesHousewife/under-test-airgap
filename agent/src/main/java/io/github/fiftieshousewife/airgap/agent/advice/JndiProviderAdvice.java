package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.util.Hashtable;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of {@code InitialContext.getDefaultInitCtx}, which makes the context its settings name, and
 * reads the provider URL from those settings.
 */
public final class JndiProviderAdvice {

    private JndiProviderAdvice() {
    }

    @Advice.OnMethodEnter
    @SuppressWarnings("PMD.ReplaceHashtableWithMap") // The JDK's own field's type
    static void enter(@Advice.FieldValue("myProps") final Hashtable<?, ?> environment) {
        Gate.jndiProvider(environment);
    }
}
