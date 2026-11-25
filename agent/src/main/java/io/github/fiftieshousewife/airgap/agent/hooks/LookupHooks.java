package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.LookupAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.OwnNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ReverseLookupAdvice;
import java.net.InetAddress;

/**
 * The host-lookup hooks.
 *
 * <ul>
 * <li>{@code ALL}: the JDK's own forward lookup, above its cache, which {@code getByName}, {@code getAllByName} and the
 * JDK's own name checks (in {@code SocketPermission}, security policies and RMI) all reach: on each JDK, its private
 * {@code getAllByName0} with the most parameters.</li>
 * <li>{@code OWN_NAME}: {@code InetAddress.getLocalHost}, the machine asking its own name, which is local, so lookups
 * in it go ahead.</li>
 * <li>{@code REVERSE}: {@code getHostName} and {@code getCanonicalHostName}, for an address with no name.</li>
 * </ul>
 */
public final class LookupHooks {

    public static final HookSpec ALL = new HookSpec("java.net.InetAddress", HookMethods.innermostLookup(),
            LookupAdvice.class);
    public static final HookSpec OWN_NAME = new HookSpec("java.net.InetAddress",
            named("getLocalHost").and(takesArguments(0)), OwnNameAdvice.class);
    public static final HookSpec REVERSE = new HookSpec("java.net.InetAddress",
            named("getHostFromNameService").and(takesArgument(0, InetAddress.class)), ReverseLookupAdvice.class);

    private LookupHooks() {
    }
}
