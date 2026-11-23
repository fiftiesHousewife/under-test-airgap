package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The test running on each thread, and what it allows. Threads a test starts share it while it runs, so a pool the
 * test fills works for it; once it ends, none of them see it, so a pool that outlives its test (OkHttp's, Hikari's,
 * Spring's) isn't allowed what the test was, nor blamed for its calls.
 *
 * <p>Scopes nest: a test class's scope covers its set-up (BeforeAll, the test instance, Spring's start-up) and
 * tear-down, and each of its tests runs in a scope inside it that starts with what the class allows. When the test
 * ends, the class's scope is back.</p>
 */
final class RunningTests {

    private static final InheritableThreadLocal<Scope> SCOPE = new InheritableThreadLocal<>();

    private RunningTests() {
    }

    /** A test, as seen by its thread and the threads it starts. */
    private static final class Scope {
        private final String test;
        private final Scope outer;
        private volatile List<String> entries;
        private volatile Allowlist allowances;
        private volatile boolean running = true;

        Scope(final String test, final Scope outer) {
            this.test = test;
            this.outer = outer;
            this.entries = outer == null ? new ArrayList<String>() : outer.entries;
            this.allowances = outer == null ? CallContext.NONE.getAllowances() : outer.allowances;
        }
    }

    private static Scope running() {
        final Scope scope = SCOPE.get();
        return scope != null && scope.running ? scope : null;
    }

    static void enter(final String test) {
        SCOPE.set(new Scope(test, running()));
    }

    static String current() {
        final Scope scope = running();
        return scope == null ? null : scope.test;
    }

    static void leave() {
        final Scope scope = SCOPE.get();
        if (scope == null) {
            return;
        }
        scope.running = false;
        if (scope.outer != null && scope.outer.running) {
            SCOPE.set(scope.outer);
        } else {
            SCOPE.remove();
        }
    }

    /** Adds to what the scope on this thread allows, starting one for no test if there is none. */
    static void allow(final String... targets) {
        Scope scope = running();
        if (scope == null) {
            scope = new Scope(null, null);
            SCOPE.set(scope);
        }
        final List<String> entries = new ArrayList<>(scope.entries);
        entries.addAll(Arrays.asList(targets));
        scope.allowances = new Allowlist(entries);
        scope.entries = entries;
    }

    static CallContext context() {
        final Scope scope = running();
        return scope == null ? CallContext.NONE : new CallContext(scope.test, scope.allowances);
    }
}
