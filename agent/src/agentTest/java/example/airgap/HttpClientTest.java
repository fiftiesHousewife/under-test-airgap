package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.HandOver;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The JDK's HTTP client (JDK 11 and later) runs requests on its own threads; a call it makes is still named by the
 * test and the line that sent the request, and allowed what that test allows. Reached by reflection, since these
 * tests compile for Java 8. The client is made, and its threads started, before any test runs, as a client in a
 * static field is.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class HttpClientTest {

    private static final String TEST = "example.HttpClientTest.sendsARequest";
    private static Object client;
    private static ExecutorService elsewhere;

    @BeforeAll
    static void makeTheClientAndAThreadBeforeAnyTest() throws ReflectiveOperationException, InterruptedException,
            ExecutionException {
        elsewhere = Executors.newSingleThreadExecutor();
        elsewhere.submit(() -> { }).get();
        if (JdkHttpClient.present()) {
            client = JdkHttpClient.client();
            JdkHttpClient.sendAsync(client, "http://192.0.2.20:80/warm-up").handle((response, failure) -> null).join();
        }
    }

    @AfterAll
    static void stop() {
        elsewhere.shutdownNow();
    }

    private static List<String> linesAbout(final String target) throws IOException {
        return Files.readAllLines(Paths.get(System.getProperty("airgap.test.report")), StandardCharsets.UTF_8)
                .stream().filter(line -> line.contains("\"target\":\"" + target)).collect(Collectors.toList());
    }

    @Test
    void namesTheTestAndTheSenderOfARefusedRequest() throws IOException {
        assumeTrue(JdkHttpClient.present(), "the HTTP client is in JDK 11 and later");
        final Throwable sent;
        final Throwable sentAsync;
        Gate.enterTest(TEST);
        try {
            sent = failureOf(() -> JdkHttpClient.send(client, "http://192.0.2.21:80/orders"));
            sentAsync = failureOf(() -> JdkHttpClient.sendAsync(client, "http://192.0.2.22:80/orders").get());
        } finally {
            Gate.leaveTest();
        }
        assertAll(
                () -> assertThat(sent).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThat(sentAsync).isInstanceOf(ConnectException.class),
                () -> assertThat(linesAbout("192.0.2.21:80")).isNotEmpty().allSatisfy(line -> assertThat(line)
                        .contains("\"kind\":\"tcp\"", "\"test\":\"" + TEST + "\"",
                                "\"caller\":\"" + HttpClientTest.class.getName())),
                () -> assertThat(linesAbout("192.0.2.22:80")).isNotEmpty().allSatisfy(line -> assertThat(line)
                        .contains("\"kind\":\"tcp\"", "\"test\":\"" + TEST + "\"",
                                "\"caller\":\"" + HttpClientTest.class.getName())));
    }

    @Test
    void aHandedOverTaskIsAllowedWhatItsTestAllowsUntilTheTestEnds() throws InterruptedException, ExecutionException {
        final String[] seen = new String[1];
        final Runnable look = () -> seen[0] = Gate.currentTest() + " " + Gate.isAllowed("handed.example.invalid", 8443);
        final Runnable carried;
        final String during;
        final String duringByExecutor;
        Gate.enterTest(TEST);
        try {
            Gate.allowForThread("handed.example.invalid:8443");
            carried = HandOver.carry(look);
            elsewhere.submit(carried).get();
            during = seen[0];
            CompletableFuture.runAsync(look, HandOver.carryingTo(elsewhere)).get();
            duringByExecutor = seen[0];
        } finally {
            Gate.leaveTest();
        }
        elsewhere.submit(carried).get();
        final String after = seen[0];
        elsewhere.submit(look).get();
        final String unwrapped = seen[0];

        assertAll(
                () -> assertThat(during).isEqualTo(TEST + " true"),
                () -> assertThat(duringByExecutor).isEqualTo(TEST + " true"),
                () -> assertThat(after).as("the test has ended").isEqualTo("null false"),
                () -> assertThat(unwrapped).isEqualTo("null false"),
                () -> assertThat(HandOver.carry(null)).isNull(),
                () -> assertThat(HandOver.carry(carried)).isSameAs(carried),
                () -> assertThat(HandOver.carryingTo(null)).isNull());
    }

    /** A call that must fail, and its cause, unwrapped from reflection and futures. */
    private interface Call {
        Object make() throws ReflectiveOperationException, InterruptedException, ExecutionException;
    }

    private static Throwable failureOf(final Call call) {
        try {
            call.make();
            return null;
        } catch (InvocationTargetException | ExecutionException wrapped) {
            return wrapped.getCause();
        } catch (ReflectiveOperationException | InterruptedException thrown) {
            return thrown;
        }
    }

    /** The JDK 11 HTTP client's API, by reflection. */
    private static final class JdkHttpClient {

        private JdkHttpClient() {
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
}
