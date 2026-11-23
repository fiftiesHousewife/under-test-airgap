package io.github.fiftieshousewife.airgap.agent;

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

    /**
     * @param frames a stack, innermost first
     * @return the project's frame in it, or {@code null} if it has none
     */
    String callerOf(final StackTraceElement... frames) {
        final String caller = callers.caller(frames);
        return Callers.UNKNOWN.equals(caller) ? null : caller;
    }

    /** The project's frame on this thread, or else the one a handed-over task carries. */
    private String callerOrCarried(final StackTraceElement... frames) {
        final String caller = callers.caller(frames);
        final String carried = HandOver.carriedCaller();
        return Callers.UNKNOWN.equals(caller) && carried != null ? carried : caller;
    }

    private Call.CallBuilder call(final Kind kind, final Target target, final CallContext context) {
        final StackTraceElement[] frames = new Throwable().getStackTrace();
        return Call.builder()
                .kind(kind)
                .target(target.text())
                .run(settings.getRun())
                .javaVersion(System.getProperty("java.version", "unknown"))
                .test(context.getTest())
                .caller(callerOrCarried(frames))
                .stack(callers.trimmed(frames));
    }
}
