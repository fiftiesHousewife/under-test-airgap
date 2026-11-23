package io.github.fiftieshousewife.airgap.junit;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Gives the running test what its own method's {@link AllowCalls} allows, on top of what its class allows, which
 * {@link AirgapListener} gives it. The allowances end with the test, when the listener tells the agent the test has
 * finished.
 */
public final class AllowCallsExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(final ExtensionContext context) {
        if (context.getTestMethod().isPresent()) {
            GateBridge.agent().allow(Allowances.on(context.getTestMethod().get()));
        }
    }
}
