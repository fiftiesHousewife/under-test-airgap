package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;

/**
 * Decides each risky call: allowed, refused, or (in {@link Mode#REPORT}) let through and reported; and records it.
 * Calls to the machine itself are always allowed, and not recorded, since test runners and build tools make
 * thousands of them.
 */
public final class Decider {

    private final Settings settings;
    private final Report report;
    private final Callers callers;

    /**
     * A decider.
     *
     * @param settings the agent's settings
     * @param report where each call is recorded
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "The report is shared on purpose: the gate writes to it")
    public Decider(final Settings settings, final Report report) {
        this.settings = settings;
        this.report = report;
        this.callers = new Callers(settings.getProject());
    }

    /**
     * Decides one call.
     *
     * @param kind the kind of call
     * @param target what it tried to reach
     * @param context the running test and its allowances
     * @return the exception to refuse the call with, or {@code null} to let it go ahead
     */
    public Throwable decide(final Kind kind, final Target target, final CallContext context) {
        if (target.isLocal()) {
            return null;
        }
        final String allowedBy = allowedBy(kind, target, context);
        final boolean refused = allowedBy == null && settings.getMode() == Mode.REFUSE;
        try {
            report.record(call(kind, target, context).outcome(outcome(refused, allowedBy)).allowedBy(allowedBy)
                    .build());
        } catch (IOException unreported) {
            // A call nobody would know about must not happen, whatever the mode or allowlist says.
            return Refusals.forUnreportedCall(kind, target, unreported);
        }
        return refused ? Refusals.forCall(kind, target) : null;
    }

    /**
     * Whether a call would be allowed, without recording anything.
     *
     * @param kind the kind of call
     * @param target what it would reach
     * @param context the running test and its allowances
     * @return true if the settings or the test allow it
     */
    public boolean allows(final Kind kind, final Target target, final CallContext context) {
        return allowedBy(kind, target, context) != null;
    }

    /**
     * Whether a class asking for a native library is the build tool's own, so its libraries go ahead unreported.
     *
     * @param className the class
     * @return true for Gradle's and Maven's own classes
     */
    public boolean isBuildToolsOwn(final String className) {
        return BuildTools.own(className, settings.getRun());
    }

    private String allowedBy(final Kind kind, final Target target, final CallContext context) {
        if (settings.getAllowlist().allows(kind, target)) {
            return "settings";
        }
        return context.getAllowances().allows(kind, target) ? "test" : null;
    }

    private String outcome(final boolean refused, final String allowedBy) {
        if (refused) {
            return "refused";
        }
        return allowedBy == null ? "reported" : "allowed";
    }

    private Call.CallBuilder call(final Kind kind, final Target target, final CallContext context) {
        final StackTraceElement[] frames = new Throwable().getStackTrace();
        return Call.builder()
                .kind(kind)
                .target(target.text())
                .run(settings.getRun())
                .javaVersion(System.getProperty("java.version", "unknown"))
                .test(context.getTest())
                .caller(callers.caller(frames))
                .stack(callers.trimmed(frames));
    }
}
