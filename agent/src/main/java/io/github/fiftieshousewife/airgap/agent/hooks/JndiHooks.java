package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.JndiCompositeNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiProviderAdvice;
import javax.naming.Name;

/**
 * The JNDI hooks. They only see names: the connections JNDI providers then make are also caught by the network hooks,
 * which is how a context made without a URL is caught.
 *
 * <ul>
 * <li>{@code NAME}: names written as strings: every lookup, bind and list on an {@code InitialContext} reaches it.</li>
 * <li>{@code COMPOSITE_NAME}: names given as {@link Name} objects.</li>
 * <li>{@code PROVIDER}: the directory a context's settings name, as its provider URL.</li>
 * </ul>
 */
public final class JndiHooks {

    public static final HookSpec NAME = new HookSpec("javax.naming.InitialContext",
            named("getURLOrDefaultInitCtx").and(takesArguments(String.class)), JndiNameAdvice.class);
    public static final HookSpec COMPOSITE_NAME = new HookSpec("javax.naming.InitialContext",
            named("getURLOrDefaultInitCtx").and(takesArguments(Name.class)), JndiCompositeNameAdvice.class);
    public static final HookSpec PROVIDER = new HookSpec("javax.naming.InitialContext",
            named("getDefaultInitCtx").and(takesArguments(0)), JndiProviderAdvice.class);

    private JndiHooks() {
    }
}
