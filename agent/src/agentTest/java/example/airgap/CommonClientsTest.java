package example.airgap;

import static example.airgap.ClientCalls.assertCallerIs;
import static example.airgap.ClientCalls.assertRefused;
import static example.airgap.ClientCalls.assertReported;
import static example.airgap.ClientCalls.failureOf;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// The HTTP and network clients old code most often reaches other machines with: Apache HttpClient 4 and 5, OkHttp and
// Netty. Each one's call is refused, fails as it would offline, and is reported with the right kind, on every
// supported JDK. The JDBC drivers are proved in JdbcDriversTest, and the JDK's own HTTP client in HttpClientTest.
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CommonClientsTest {

    @Test
    void apacheHttpClient4() throws IOException {
        final Throwable failure;
        try (org.apache.http.impl.client.CloseableHttpClient client =
                     org.apache.http.impl.client.HttpClients.createDefault()) {
            failure = failureOf(() -> client.execute(new org.apache.http.client.methods.HttpGet(
                    "http://192.0.2.31:80/orders")));
        }

        assertAll(
                () -> assertRefused(failure, ConnectException.class),
                () -> assertReported("192.0.2.31:80", "tcp"),
                () -> assertCallerIs(CommonClientsTest.class, "192.0.2.31:80"));
    }

    @Test
    void apacheHttpClient5() throws IOException {
        final Throwable failure;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            failure = failureOf(() -> client.execute(new HttpGet("http://192.0.2.32:80/orders"), response -> null));
        }

        assertAll(
                () -> assertRefused(failure, ConnectException.class),
                () -> assertReported("192.0.2.32:80", "tcp"));
    }

    @Test
    void okHttp() {
        final OkHttpClient client = new OkHttpClient();
        final Throwable failure = failureOf(() -> client.newCall(new Request.Builder()
                .url("http://192.0.2.33:80/orders").build()).execute().close());
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();

        assertAll(
                () -> assertRefused(failure, ConnectException.class),
                () -> assertReported("192.0.2.33:80", "tcp"));
    }

    @Test
    void netty() throws InterruptedException {
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
                () -> assertRefused(failure, ConnectException.class),
                () -> assertReported("192.0.2.34:80", "tcp"));
    }
}
