package io.github.fiftieshousewife.airgap.junit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reaches the agent's gate, which the agent puts on the bootstrap class path. Found by name, so a project can keep
 * this jar on its test classpath and run without the agent: every call is then ignored.
 */
final class GateBridge {

    static final String GATE = "io.github.fiftieshousewife.airgap.agent.Gate";

    private static final GateBridge ATTACHED = load(GATE);

    private final Method enterTestMethod;
    private final Method leaveTestMethod;
    private final Method allowForThreadMethod;

    private GateBridge(final Method enterTest, final Method leaveTest, final Method allowForThread) {
        this.enterTestMethod = enterTest;
        this.leaveTestMethod = leaveTest;
        this.allowForThreadMethod = allowForThread;
    }

    /** @return the bridge to the attached agent, which does nothing if no agent is attached */
    static GateBridge agent() {
        return ATTACHED;
    }

    static GateBridge load(final String gateName) {
        try {
            final Class<?> gate = Class.forName(gateName, false, null);
            return new GateBridge(gate.getMethod("enterTest", String.class), gate.getMethod("leaveTest"),
                    gate.getMethod("allowForThread", String[].class));
        } catch (ClassNotFoundException | NoSuchMethodException noAgent) {
            return new GateBridge(null, null, null);
        }
    }

    /** @return whether an agent is attached */
    boolean isAttached() {
        return enterTestMethod != null;
    }

    void enterTest(final String test) {
        invoke(enterTestMethod, test);
    }

    void leaveTest() {
        invoke(leaveTestMethod);
    }

    void allow(final String... targets) {
        invoke(allowForThreadMethod, (Object) targets);
    }

    private static void invoke(final Method method, final Object... arguments) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, arguments);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("airgap: couldn't reach the agent's gate", e);
        }
    }
}
