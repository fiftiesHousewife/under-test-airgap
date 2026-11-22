package io.github.fiftieshousewife.airgap.agent;

import java.util.Arrays;

/**
 * The test running on each thread, and what it allows. Threads a test starts share it while it runs, so a pool the
 * test fills works for it; once it ends, none of them see it, so a pool that outlives its test (OkHttp's, Hikari's,
 * Spring's) isn't allowed what the test was, nor blamed for its calls.
 */
final class RunningTests {

    private static final InheritableThreadLocal<Scope> SCOPE = new InheritableThreadLocal<>();

    private RunningTests() {
    }

    /** A test, as seen by its thread and the threads it starts. */
    private static final class Scope {
        private final String test;
        private volatile Allowlist allowances = CallContext.NONE.getAllowances();
        private volatile boolean running = true;

        Scope(final String test) {
            this.test = test;
        }
    }

    private static Scope running() {
        final Scope scope = SCOPE.get();
        return scope != null && scope.running ? scope : null;
    }

    static void enter(final String test) {
        SCOPE.set(new Scope(test));
    }

    static String current() {
        final Scope scope = running();
        return scope == null ? null : scope.test;
    }

    static void leave() {
        final Scope scope = SCOPE.get();
        if (scope != null) {
            scope.running = false;
        }
        SCOPE.remove();
    }

    static void allow(final String... targets) {
        Scope scope = running();
        if (scope == null) {
            scope = new Scope(null);
            SCOPE.set(scope);
        }
        scope.allowances = new Allowlist(Arrays.asList(targets));
    }

    static CallContext context() {
        final Scope scope = running();
        return scope == null ? CallContext.NONE : new CallContext(scope.test, scope.allowances);
    }
}
