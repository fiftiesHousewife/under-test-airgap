package io.github.fiftieshousewife.airgap.agent.hooks;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** One hook's JDK class, the methods in it the hook rewrites, and the advice it adds to them. */
public final class HookSpec {

    private final String typeName;
    private final ElementMatcher<? super MethodDescription> methods;
    private final Class<?> advice;

    /**
     * @param typeName the JDK class
     * @param methods the methods in it
     * @param advice the advice added to them
     */
    public HookSpec(final String typeName, final ElementMatcher<? super MethodDescription> methods,
            final Class<?> advice) {
        this.typeName = typeName;
        this.methods = methods;
        this.advice = advice;
    }

    /** @return the JDK class */
    public String getTypeName() {
        return typeName;
    }

    /** @return the methods the hook rewrites */
    public ElementMatcher<? super MethodDescription> getMethods() {
        return methods;
    }

    /** @return the advice added to them */
    public Class<?> getAdvice() {
        return advice;
    }
}
