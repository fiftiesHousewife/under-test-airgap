package example.airgap;

import java.lang.reflect.InvocationTargetException;
import java.net.ProxySelector;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

// The JDK 11 HTTP client's API, by reflection, since these tests compile for Java 8; and the failure of a call made
// through it, unwrapped from reflection and futures.
final class JdkHttpClient {

    private JdkHttpClient() {
    }

    // A call that must fail.
    interface Call {
        Object make() throws ReflectiveOperationException, InterruptedException, ExecutionException;
    }

    static Throwable failureOf(final Call call) {
        try {
            call.make();
            return null;
        } catch (InvocationTargetException | ExecutionException wrapped) {
            return wrapped.getCause();
        } catch (ReflectiveOperationException | InterruptedException thrown) {
            return thrown;
        }
    }

    static boolean present() {
        try {
            Class.forName("java.net.http.HttpClient");
            return true;
        } catch (ClassNotFoundException jdk8) {
            return false;
        }
    }

    static Object client() throws ReflectiveOperationException {
        return Class.forName("java.net.http.HttpClient").getMethod("newHttpClient").invoke(null);
    }

    // A client that sends every request through the proxies the selector picks.
    static Object clientThrough(final ProxySelector selector) throws ReflectiveOperationException {
        final Object builder = Class.forName("java.net.http.HttpClient").getMethod("newBuilder").invoke(null);
        final Class<?> builderType = Class.forName("java.net.http.HttpClient$Builder");
        builderType.getMethod("proxy", ProxySelector.class).invoke(builder, selector);
        return builderType.getMethod("build").invoke(builder);
    }

    static int statusOf(final Object response) throws ReflectiveOperationException {
        return (Integer) Class.forName("java.net.http.HttpResponse").getMethod("statusCode").invoke(response);
    }

    private static Object request(final String uri) throws ReflectiveOperationException {
        final Object builder = Class.forName("java.net.http.HttpRequest").getMethod("newBuilder", URI.class)
                .invoke(null, URI.create(uri));
        return Class.forName("java.net.http.HttpRequest$Builder").getMethod("build").invoke(builder);
    }

    private static Object discarding() throws ReflectiveOperationException {
        return Class.forName("java.net.http.HttpResponse$BodyHandlers").getMethod("discarding").invoke(null);
    }

    static Object send(final Object client, final String uri) throws ReflectiveOperationException {
        return Class.forName("java.net.http.HttpClient").getMethod("send",
                Class.forName("java.net.http.HttpRequest"), Class.forName("java.net.http.HttpResponse$BodyHandler"))
                .invoke(client, request(uri), discarding());
    }

    static CompletableFuture<?> sendAsync(final Object client, final String uri)
            throws ReflectiveOperationException {
        return (CompletableFuture<?>) Class.forName("java.net.http.HttpClient").getMethod("sendAsync",
                Class.forName("java.net.http.HttpRequest"), Class.forName("java.net.http.HttpResponse$BodyHandler"))
                .invoke(client, request(uri), discarding());
    }
}
