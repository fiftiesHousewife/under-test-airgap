package io.github.fiftieshousewife.airgap.agent;

import io.github.fiftieshousewife.airgap.agent.spring.SpringEnvironments;
import io.github.fiftieshousewife.airgap.agent.spring.SpringSettings;
import java.util.List;

/**
 * Where the hooks in Spring's own classes send their calls, and what the report says about a call made in a Spring
 * application: the setting that held its address, the active profiles, and whether Spring was starting. A context
 * started with a production profile active fails to start. Loaded by the bootstrap class loader, like {@link Gate}.
 * See the decision "In Spring applications, the report names the setting and profiles behind each call, and a
 * production profile fails start-up".
 */
public final class SpringGate {

    private SpringGate() {
    }

    /** A Spring {@code Environment} has been made. */
    public static void environmentMade(final Object environment) {
        SpringEnvironments.add(environment);
    }

    /** Spring Boot is starting an application on this thread. */
    public static void applicationStarting() {
        SpringEnvironments.startUpBegins();
    }

    /** Spring has finished starting an application or context on this thread, or failed to. */
    public static void startUpEnded() {
        SpringEnvironments.startUpEnds();
    }

    /** A Spring context is being refreshed: it fails to start if a production profile is active. */
    public static void contextRefreshing(final Object context) {
        final Decider decider = Gate.currentDecider();
        final Object environment = SpringEnvironments.of(context);
        if (environment != null) {
            SpringEnvironments.refreshed(environment);
        }
        if (decider != null && environment != null) {
            stopUnderAProductionProfile(SpringEnvironments.activeProfiles(environment),
                    decider.productionProfiles());
        }
        SpringEnvironments.startUpBegins();
    }

    private static void stopUnderAProductionProfile(final List<String> active, final List<String> production) {
        for (final String profile : active) {
            if (isOneOf(profile, production)) {
                throw new IllegalStateException("airgap: Spring is starting with the production profile '" + profile
                        + "' active, so the airgap agent stopped it before it could reach anything. Run with a test"
                        + " profile, or list your production profiles in testing/airgap.yaml (production_profiles).");
            }
        }
    }

    private static boolean isOneOf(final String profile, final List<String> production) {
        for (final String name : production) {
            if (Ascii.equalsIgnoringCase(name, profile)) {
                return true;
            }
        }
        return false;
    }

    // Adds what's known from Spring to a call's report line: called while the call is decided.
    static Call.CallBuilder describe(final Call.CallBuilder call, final Target target) {
        final List<Object> environments = SpringEnvironments.newestFirst();
        if (environments.isEmpty()) {
            return call;
        }
        for (final Object environment : environments) {
            final String[] setting = SpringSettings.holding(environment, target.getName());
            if (setting.length > 0) {
                return call.settingName(setting[0]).settingFrom(setting[1])
                        .profiles(SpringEnvironments.activeProfiles(environment))
                        .springStartUp(SpringEnvironments.isStartingUp());
            }
        }
        final Object refreshed = SpringEnvironments.lastRefreshed();
        return call.profiles(refreshed == null ? null : SpringEnvironments.activeProfiles(refreshed))
                .springStartUp(SpringEnvironments.isStartingUp());
    }
}
