package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.HandOver;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// A task handed to a thread the test didn't start, as the JDK's HTTP client hands its requests over, is named by its
// test and allowed what that test allows, until the test ends. The thread is started before any test runs, as a pool
// in a static field is, so it inherits nothing from a test.
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class HandOverTest {

    private static final String TEST = "example.HandOverTest.handsATaskOver";
    private static ExecutorService elsewhere;
    private final String[] seen = new String[1];

    @BeforeAll
    static void startAThreadBeforeAnyTest() throws InterruptedException, ExecutionException {
        elsewhere = Executors.newSingleThreadExecutor();
        elsewhere.submit(() -> { }).get();
    }

    @AfterAll
    static void stop() {
        elsewhere.shutdownNow();
    }

    // A task that notes the test it runs for, and whether it may reach the stand-in that test allows
    private Runnable look() {
        return () -> seen[0] = Gate.currentTest() + " " + Gate.isAllowed("handed.example.invalid", 8443);
    }

    // A task handed over while the test runs, with what the test allows
    private Runnable carriedFromTheTest() {
        Gate.enterTest(TEST);
        try {
            Gate.allowForThread("handed.example.invalid:8443");
            return HandOver.carry(look());
        } finally {
            Gate.leaveTest();
        }
    }

    @Test
    void aHandedOverTaskIsAllowedWhatItsTestAllowsWhileItRuns() throws InterruptedException, ExecutionException {
        final String carried;
        final String byExecutor;
        Gate.enterTest(TEST);
        try {
            Gate.allowForThread("handed.example.invalid:8443");
            elsewhere.submit(HandOver.carry(look())).get();
            carried = seen[0];
            CompletableFuture.runAsync(look(), HandOver.carryingTo(elsewhere)).get();
            byExecutor = seen[0];
        } finally {
            Gate.leaveTest();
        }

        assertAll(
                () -> assertThat(carried).isEqualTo(TEST + " true"),
                () -> assertThat(byExecutor).isEqualTo(TEST + " true"));
    }

    @Test
    void aHandedOverTaskIsNothingOnceItsTestEnds() throws InterruptedException, ExecutionException {
        elsewhere.submit(carriedFromTheTest()).get();
        final String after = seen[0];
        elsewhere.submit(look()).get();
        final String unwrapped = seen[0];

        assertAll(
                () -> assertThat(after).as("the test has ended").isEqualTo("null false"),
                () -> assertThat(unwrapped).isEqualTo("null false"));
    }

    @Test
    void leavesNothingToCarryAndWhatsCarriedAlreadyAsTheyAre() {
        final Runnable carried = carriedFromTheTest();

        assertAll(
                () -> assertThat(HandOver.carry(null)).isNull(),
                () -> assertThat(HandOver.carry(carried)).isSameAs(carried),
                () -> assertThat(HandOver.carryingTo(null)).isNull());
    }
}
