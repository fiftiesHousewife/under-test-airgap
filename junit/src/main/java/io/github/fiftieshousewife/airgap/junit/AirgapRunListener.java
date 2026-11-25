package io.github.fiftieshousewife.airgap.junit;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

/**
 * Tells the agent which test is running, for JUnit 3 and 4 tests run by JUnit 4 itself rather than on the JUnit
 * Platform, as Surefire's JUnit 4 provider runs them, so the report names the test behind each call. JUnit 4 has no
 * service loader for listeners: the build names this one, as the airgap Maven extension does with Surefire's
 * {@code listener} property. Each test is allowed what the {@link AllowCalls} on its class, its base classes and the
 * classes it is nested in allow.
 */
public final class AirgapRunListener extends RunListener {

    @Override
    public void testStarted(final Description test) {
        GateBridge.agent().enterTest(name(test));
        GateBridge.agent().allow(Allowances.of(test.getTestClass()));
    }

    @Override
    public void testFinished(final Description test) {
        GateBridge.agent().leaveTest();
    }

    // Returns the test as class.method, or its display name when JUnit gives no method
    static String name(final Description test) {
        return test.getMethodName() == null ? test.getDisplayName() : test.getClassName() + "." + test.getMethodName();
    }
}
