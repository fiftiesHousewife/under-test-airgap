package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Refusals;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The clients old code most often reaches other machines with: Apache HttpClient 4 and 5, OkHttp, Netty, and the
 * PostgreSQL and MySQL JDBC drivers. Each one's call is refused, fails as it would offline, and is reported with the
 * right kind, on every supported JDK. The JDK's own HTTP client is proved in {@link HttpClientTest}.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CommonClientsTest {

    private static List<String> linesAbout(final String target) throws IOException {
        return Files.readAllLines(Paths.get(System.getProperty("airgap.test.report")), StandardCharsets.UTF_8)
                .stream().filter(line -> line.contains("\"target\":\"" + target + "\"")).collect(Collectors.toList());
    }

    /** A failure caused, somewhere down its chain, by the agent's refusal. */
    private static void assertRefused(final Throwable failure, final Class<? extends Throwable> offline) {
        Throwable cause = failure;
        boolean refused = false;
        boolean asOffline = false;
        while (cause != null) {
            refused |= cause.getMessage() != null && cause.getMessage().contains(Refusals.REFUSED);
            asOffline |= offline.isInstance(cause);
            cause = cause.getCause();
        }
        assertThat(refused && asOffline).as("refused by the agent, failing with %s: %s", offline.getSimpleName(),
                failure).isTrue();
    }

    private static void assertReported(final String target, final String kind) throws IOException {
        assertThat(linesAbout(target)).as(target).isNotEmpty().allSatisfy(line -> assertThat(line)
                .contains("\"kind\":\"" + kind + "\"", "\"outcome\":\"refused\""));
    }

    /**
     * The report names this test's line, not the client library's: the tests' settings file lists the project's own
     * packages, as a project run by the try-it script, which can't name them, would.
     */
    private static void assertCallerIsTheTest(final String target) throws IOException {
        assertThat(linesAbout(target)).as(target).isNotEmpty().allSatisfy(line -> assertThat(line)
                .contains("\"caller\":\"" + CommonClientsTest.class.getName()));
    }

    private static Throwable failureOf(final Call call) {
        try {
            call.make();
            return new AssertionError("the call went ahead");
        } catch (IOException | SQLException | RuntimeException | AssertionError failed) {
            return failed;
        }
    }

    @Test
    void apacheHttpClient4() throws IOException {
        final Throwable failure;
        try (org.apache.http.impl.client.CloseableHttpClient client =
                     org.apache.http.impl.client.HttpClients.createDefault()) {
            failure = failureOf(() -> client.execute(new org.apache.http.client.methods.HttpGet(
                    "http://192.0.2.31:80/orders")));
        }

        assertAll(
                () -> assertRefused(failure, java.net.ConnectException.class),
                () -> assertReported("192.0.2.31:80", "tcp"),
                () -> assertCallerIsTheTest("192.0.2.31:80"));
    }

    @Test
    void apacheHttpClient5() throws IOException {
        final Throwable failure;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            failure = failureOf(() -> client.execute(new HttpGet("http://192.0.2.32:80/orders"), response -> null));
        }

        assertAll(
                () -> assertRefused(failure, java.net.ConnectException.class),
                () -> assertReported("192.0.2.32:80", "tcp"));
    }

    @Test
    void okHttp() throws IOException {
        final OkHttpClient client = new OkHttpClient();
        final Throwable failure = failureOf(() -> client.newCall(new Request.Builder()
                .url("http://192.0.2.33:80/orders").build()).execute().close());
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();

        assertAll(
                () -> assertRefused(failure, java.net.ConnectException.class),
                () -> assertReported("192.0.2.33:80", "tcp"));
    }

    @Test
    void netty() throws IOException, InterruptedException {
        final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final Throwable failure;
        try {
            final ChannelFuture connect = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter()).connect(new InetSocketAddress("192.0.2.34", 80));
            connect.await();
            failure = connect.cause();
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).await();
        }

        assertAll(
                () -> assertRefused(failure, java.net.ConnectException.class),
                () -> assertReported("192.0.2.34:80", "tcp"));
    }

    @Test
    void postgresqlDriver() throws IOException {
        final Throwable byAddress = failureOf(() -> DriverManager.getConnection(
                "jdbc:postgresql://192.0.2.35:5432/orders").close());
        final Throwable byName = failureOf(() -> DriverManager.getConnection(
                "jdbc:postgresql://db.example.invalid:5432/orders").close());

        assertAll(
                () -> assertThat(byAddress).isInstanceOf(SQLException.class),
                () -> assertRefused(byAddress, java.net.ConnectException.class),
                () -> assertReported("192.0.2.35:5432", "tcp"),
                () -> assertRefused(byName, UnknownHostException.class),
                () -> assertReported("db.example.invalid", "host-lookup"));
    }

    @Test
    void mysqlDriver() throws IOException {
        final Throwable failure = failureOf(() -> DriverManager.getConnection(
                "jdbc:mysql://192.0.2.36:3306/orders").close());

        assertAll(
                () -> assertThat(failure).isInstanceOf(SQLException.class),
                () -> assertRefused(failure, java.net.ConnectException.class),
                () -> assertReported("192.0.2.36:3306", "tcp"),
                () -> assertCallerIsTheTest("192.0.2.36:3306"));
    }

    /** A call that must fail. */
    private interface Call {
        void make() throws IOException, SQLException;
    }
}
