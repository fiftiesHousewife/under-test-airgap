package io.github.fiftieshousewife.airgap.agent.advice;

import io.github.fiftieshousewife.airgap.agent.NetworkGate;
import java.net.Proxy;
import net.bytebuddy.asm.Advice;

/**
 * Copied into the start of the JDK's {@code sun.net.www.http.HttpClient.openServer()}, which every
 * {@code HttpURLConnection} reaches to open its server: through an HTTP proxy, the destination is checked as well as
 * the proxy, which the socket hooks then check.
 */
public final class UrlConnectionProxyAdvice {

    private UrlConnectionProxyAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.FieldValue("host") final String host, @Advice.FieldValue("port") final int port,
            @Advice.FieldValue("proxy") final Proxy proxy) {
        NetworkGate.urlConnectionServer(host, port, proxy);
    }
}
