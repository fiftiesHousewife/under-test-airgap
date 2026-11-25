package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.NetworkGate;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of the JDK HTTP client's private {@code sendAsync}, which every request reaches, from JDK 11:
 * through an HTTP proxy the client's proxy selector picks, the request's destination is checked as well as the proxy.
 */
public final class JdkClientProxyAdvice {

    private JdkClientProxyAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.This final Object client, @Advice.Argument(0) final Object request) {
        NetworkGate.jdkClientRequest(client, request);
    }
}
