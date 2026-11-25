package io.github.fiftieshousewife.airgap.agent;

import io.github.fiftieshousewife.airgap.agent.hooks.HookMethods;
import io.github.fiftieshousewife.airgap.agent.probes.FileProbes;
import io.github.fiftieshousewife.airgap.agent.probes.NetworkProbes;
import io.github.fiftieshousewife.airgap.agent.probes.Probe;
import io.github.fiftieshousewife.airgap.agent.probes.SystemProbes;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.naming.NamingException;
import net.bytebuddy.description.type.TypeDescription;

// Proves, at start, that every hook is in place: makes each hooked call with the gate in probing mode, where a hook
// that is reached throws Gate.Reached before the call does anything. A hook that isn't reached means the JDK
// has changed under it, and the agent refuses to start rather than guard partly.
//
// Every probe is harmless even if its hook is missing: lookups and connects go to a name that can't resolve,
// reverse lookups and packets to the machine itself, the program started and the libraries loaded don't exist, the
// JNDI contexts have no provider to connect with, and the files written are in a folder that doesn't exist. A few
// hooks can't be probed that way: ending the JVM (a missing hook would end it), the machine asking its own name and
// the HTTP client's hand-overs (which don't refuse), SCTP (not every platform supports it), the foreign function API
// (probing it sets off the JDK's warning about native access) and the secure directory stream (not every system's
// JDK hands one out). Those are proved by their methods being there to rewrite, in classes the other probes prove
// rewritten, or, for SCTP, the foreign function API, the HTTP client and the secure directory stream, by the agent's
// own tests.
final class SelfCheck {

    private static final Map<Hook, Probe> PROBES = buildProbes();

    private SelfCheck() {
    }

    // Probes every hook, including any the settings left out, so leaving one out proves this check works.
    //
    // installErrors: classes that couldn't be rewritten, and why, to name in the message
    // without: hooks the settings left out, which the exit hooks' check can't otherwise see
    static void run(final List<String> installErrors, final Set<String> without) {
        final List<String> missing = new ArrayList<>();
        for (final Hook hook : Hook.values()) {
            if (isMissing(hook, without)) {
                missing.add(hook.id());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(whyItWontStart(missing, installErrors));
        }
    }

    private static String whyItWontStart(final List<String> missing, final List<String> installErrors) {
        return "The airgap agent couldn't put its hooks in place for " + missing + " on Java "
                + System.getProperty("java.version") + ", so it won't start: this JDK isn't supported yet. Nothing was"
                + " run unguarded." + (installErrors.isEmpty() ? "" : " Why: " + installErrors);
    }

    private static boolean isMissing(final Hook hook, final Set<String> without) {
        return hook.appliesHere() && !isInPlace(hook, without);
    }

    private static boolean isInPlace(final Hook hook, final Set<String> without) {
        return PROBES.containsKey(hook) ? reaches(hook, PROBES.get(hook))
                : !without.contains(hook.id()) && isThereToRewrite(hook);
    }

    private static boolean reaches(final Hook hook, final Probe probe) {
        Gate.setProbing(hook.kind());
        try {
            probe.call();
            return false;
        } catch (Gate.Reached reached) {
            // A JNDI probe whose own hook is missing could reach a network hook instead; that doesn't count.
            return reached.getKind() == hook.kind();
        } catch (InvocationTargetException wrapped) {
            final Throwable cause = wrapped.getCause();
            return cause instanceof Gate.Reached && ((Gate.Reached) cause).getKind() == hook.kind();
        } catch (IOException | NamingException | ReflectiveOperationException | RuntimeException
                | UnsatisfiedLinkError notReached) {
            return false;
        } finally {
            Gate.setProbing(null);
        }
    }

    // For a hook with no harmless probe: whether its class has the methods it rewrites.
    private static boolean isThereToRewrite(final Hook hook) {
        final Class<?> type = HookMethods.load(hook.getTypeName());
        return type != null && !TypeDescription.ForLoadedType.of(type).getDeclaredMethods()
                .filter(hook.getMethods()).isEmpty();
    }

    private static Map<Hook, Probe> buildProbes() {
        final Map<Hook, Probe> probes = new EnumMap<>(Hook.class);
        NetworkProbes.addLookups(probes);
        NetworkProbes.addTcp(probes);
        NetworkProbes.addUdp(probes);
        SystemProbes.addTo(probes);
        FileProbes.addTo(probes);
        return probes;
    }
}
