package io.github.fiftieshousewife.airgap.junit;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Tells the agent which test is running, so the report names the test behind each call. Registered automatically
 * through Java's service loader: putting the jar on the test classpath is enough. JUnit 4 tests run on the Vintage
 * engine are named the same way.
 *
 * <p>A test class is a scope of its own, so calls made while it is set up (BeforeAll, its constructor, Spring's
 * start-up) or torn down are named by the class, and allowed what the {@link AllowCalls} on the class, its base
 * classes and the classes it is nested in allow. Each of its tests is allowed the same.</p>
 */
public final class AirgapListener implements TestExecutionListener {

    @Override
    public void executionStarted(final TestIdentifier test) {
        final TestSource source = test.getSource().orElse(null);
        if (test.isTest()) {
            GateBridge.agent().enterTest(name(test));
            if (source instanceof MethodSource) {
                GateBridge.agent().allow(Allowances.of(load(((MethodSource) source).getClassName())));
            }
        } else if (source instanceof ClassSource) {
            final ClassSource testClass = (ClassSource) source;
            GateBridge.agent().enterTest(testClass.getClassName());
            GateBridge.agent().allow(Allowances.of(load(testClass.getClassName())));
        }
    }

    @Override
    public void executionFinished(final TestIdentifier test, final TestExecutionResult result) {
        if (test.isTest() || test.getSource().orElse(null) instanceof ClassSource) {
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

    // The test class, as the engine loaded it, or null if it can't be loaded here.
    private static Class<?> load(final String className) {
        try {
            return ClassSource.from(className).getJavaClass();
        } catch (RuntimeException | LinkageError notHere) {
            return null;
        }
    }
}
