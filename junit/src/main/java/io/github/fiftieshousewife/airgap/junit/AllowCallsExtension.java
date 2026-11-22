package io.github.fiftieshousewife.airgap.junit;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Gives the running test what its class's and its own {@link AllowCalls} allow. The allowances end with the test,
 * when the listener tells the agent the test has finished.
 */
public final class AllowCallsExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(final ExtensionContext context) {
        final List<String> targets = new ArrayList<>();
        add(targets, context.getTestClass());
        add(targets, context.getTestMethod());
        GateBridge.agent().allow(targets.toArray(new String[0]));
    }

    private static void add(final List<String> targets, final Optional<? extends AnnotatedElement> element) {
        if (element.isPresent()) {
            final AllowCalls allowed = element.get().getAnnotation(AllowCalls.class);
            if (allowed != null) {
                targets.addAll(Arrays.asList(allowed.value()));
            }
        }
    }
}
