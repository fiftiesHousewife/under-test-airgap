package io.github.fiftieshousewife.airgap.agent;

import java.util.Collections;

/**
 * What's known about the thread making a call: the test it's running, and what that test allows. {@link #NONE} is no
 * test running, with nothing extra allowed.
 */
public final class CallContext {

    public static final CallContext NONE = new CallContext(null, new Allowlist(Collections.<String>emptyList()));

    private final String test;
    private final Allowlist allowances;

    /**
     * A context.
     *
     * @param test the running test, as {@code class.method}, or {@code null}
     * @param allowances what the test allows, through {@code @AllowCalls}
     */
    public CallContext(final String test, final Allowlist allowances) {
        this.test = test;
        this.allowances = allowances;
    }

    /** @return the running test, or {@code null} */
    public String getTest() {
        return test;
    }

    /** @return what the running test allows */
    public Allowlist getAllowances() {
        return allowances;
    }
}
