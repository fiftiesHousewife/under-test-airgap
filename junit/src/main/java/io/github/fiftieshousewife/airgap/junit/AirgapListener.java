package io.github.fiftieshousewife.airgap.junit;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Tells the agent which test is running, so the report names the test behind each call. Registered automatically
 * through Java's service loader: putting the jar on the test classpath is enough. JUnit 4 tests run on the Vintage
 * engine are named the same way.
 */
public final class AirgapListener implements TestExecutionListener {

    @Override
    public void executionStarted(final TestIdentifier test) {
        if (test.isTest()) {
            GateBridge.agent().enterTest(name(test));
        }
    }

    @Override
    public void executionFinished(final TestIdentifier test, final TestExecutionResult result) {
        if (test.isTest()) {
            GateBridge.agent().leaveTest();
        }
    }

    static String name(final TestIdentifier test) {
        final TestSource source = test.getSource().orElse(null);
        if (source instanceof MethodSource) {
            final MethodSource method = (MethodSource) source;
            return method.getClassName() + "." + method.getMethodName();
        }
        return test.getDisplayName();
    }
}
