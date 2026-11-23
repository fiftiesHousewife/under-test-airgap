package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Refusals that reach code on other threads: through an asynchronous connect's handler, and on pool threads a test
 * started. Every target is an address reserved for documentation or a name that can't resolve.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ThreadsAndCallbacksTest {

    private static final String POOL_TARGET = "pool.example.invalid";

    /** Hands the failure, if its attachment came back with it, to a future. */
    private static final class Recording implements CompletionHandler<Void, String> {
        private final CompletableFuture<Throwable> failure = new CompletableFuture<>();

        @Override
        public void completed(final Void result, final String attachment) {
            failure.complete(null);
        }

        @Override
        public void failed(final Throwable thrown, final String attachment) {
            failure.complete("attachment".equals(attachment) ? thrown : null);
        }
    }

    @Test
    void tellsAnAsynchronousConnectsHandlerItFailed()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final Recording handler = new Recording();
        try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
            channel.connect(new InetSocketAddress("192.0.2.1", 5432), "attachment", handler);

            assertThat(handler.failure.get(5, TimeUnit.SECONDS)).as("never left waiting")
                    .isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED);
        }
    }

    private static String whatThePoolSees(final ExecutorService pool)
            throws InterruptedException, ExecutionException, TimeoutException {
        return pool.submit(() -> Gate.currentTest() + " " + Gate.isAllowed(POOL_TARGET, 443))
                .get(5, TimeUnit.SECONDS);
    }

    /** Pools started during a test, as OkHttp's, Hikari's and Spring's are, outlive it. */
    @Test
    void forgetsATestsNameAndAllowancesOnThreadsItStartedOnceItEnds()
            throws InterruptedException, ExecutionException, TimeoutException {
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Gate.enterTest("example.PoolTest.first");
            Gate.allowForThread(POOL_TARGET + ":443");
            final String during = whatThePoolSees(pool);
            Gate.leaveTest();
            final String after = whatThePoolSees(pool);

            assertAll(
                    () -> assertThat(during).as("threads the test starts are part of it")
                            .isEqualTo("example.PoolTest.first true"),
                    () -> assertThat(after).isEqualTo("null false"));
        } finally {
            Gate.leaveTest();
            pool.shutdownNow();
        }
    }

    /** A test class's scope covers its set-up; each test inside it starts with what the class allows. */
    @Test
    void aTestRunsInsideItsClassAndTheClassComesBackAfter() {
        try {
            Gate.enterTest("example.NestedTest");
            Gate.allowForThread("class.example.invalid:443");
            Gate.enterTest("example.NestedTest.first");
            Gate.allowForThread("method.example.invalid:443");
            final boolean classAllowedInTest = Gate.isAllowed("class.example.invalid", 443);
            final boolean methodAllowedInTest = Gate.isAllowed("method.example.invalid", 443);
            Gate.leaveTest();

            assertAll(
                    () -> assertThat(classAllowedInTest).isTrue(),
                    () -> assertThat(methodAllowedInTest).isTrue(),
                    () -> assertThat(Gate.currentTest()).isEqualTo("example.NestedTest"),
                    () -> assertThat(Gate.isAllowed("class.example.invalid", 443)).isTrue(),
                    () -> assertThat(Gate.isAllowed("method.example.invalid", 443)).isFalse());
        } finally {
            Gate.leaveTest();
        }
    }
}
