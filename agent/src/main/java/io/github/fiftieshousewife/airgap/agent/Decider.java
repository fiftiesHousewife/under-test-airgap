package io.github.fiftieshousewife.airgap.agent;

import java.io.IOException;

/**
 * Decides each risky call: allowed, refused, or (in {@link Mode#REPORT}) let through and reported; and records it.
 * Calls to the machine itself are always allowed, and not recorded, since test runners and build tools make
 * thousands of them. A file write outside the project is refused only when the settings say
 * {@code file_writes: refuse}; otherwise it's reported.
 */
public final class Decider {

    private final Settings settings;
    private final Report report;
    private final Callers callers;
    private final ProjectFolders folders;

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
        this.folders = ProjectFolders.of(settings.getProjectFolders());
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
        final boolean refused = allowedBy == null && refuses(kind);
        try {
            report.record(call(kind, target, context).outcome(outcome(refused, allowedBy)).allowedBy(allowedBy)
                    .build());
        } catch (IOException unreported) {
            // A call nobody would know about must not happen, whatever the mode or allowlist says.
            return Refusals.forUnreportedCall(kind, target, unreported);
        }
        return refused ? Refusals.forCall(kind, target) : null;
    }

    // Whether the settings refuse what they don't allow: every kind in refuse mode, but file writes only if asked to.
    private boolean refuses(final Kind kind) {
        return settings.getMode() == Mode.REFUSE && (kind != Kind.FILE_WRITE || settings.refusesFileWrites());
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

    /**
     * Whether a write goes inside the project's folders, so it goes ahead unreported.
     *
     * @param path the path written, absolute
     * @return true inside the build's root, the temporary folder, the report's folder or a listed folder
     */
    public boolean isProjects(final String path) {
        return folders.contains(path);
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

    // frames: a stack, innermost first
    // Returns the project's frame in it, or null if it has none
    String callerOf(final StackTraceElement... frames) {
        final String caller = callers.caller(frames);
        return Callers.NO_PROJECT_FRAME.equals(caller) ? null : caller;
    }

    // The project's frame on this thread, or else the one a handed-over task carries.
    private String callerOrCarried(final StackTraceElement... frames) {
        final String caller = callers.caller(frames);
        final String carried = HandOver.carriedCaller();
        return Callers.NO_PROJECT_FRAME.equals(caller) && carried != null ? carried : caller;
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
