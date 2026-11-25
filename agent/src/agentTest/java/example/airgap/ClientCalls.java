package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.sql.SQLException;

// What the client libraries' tests check: that a call failed because the agent refused it, as it would fail offline,
// and that the report has it, with the test's own line as its caller.
final class ClientCalls {

    private ClientCalls() {
    }

    // A call that must fail.
    interface Call {
        void make() throws IOException, SQLException;
    }

    static Throwable failureOf(final Call call) {
        try {
            call.make();
            return new AssertionError("the call went ahead");
        } catch (IOException | SQLException | RuntimeException | AssertionError failed) {
            return failed;
        }
    }

    // A failure caused, somewhere down its chain, by the agent's refusal, and failing as it would offline.
    static void assertRefused(final Throwable failure, final Class<? extends Throwable> offline) {
        boolean refused = false;
        boolean asOffline = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            refused |= String.valueOf(cause.getMessage()).contains(Refusals.REFUSED);
            asOffline |= offline.isInstance(cause);
        }
        assertThat(refused && asOffline).as("refused by the agent, failing with %s: %s", offline.getSimpleName(),
                failure).isTrue();
    }

    static void assertReported(final String target, final String kind) throws IOException {
        assertThat(ReportLines.aboutExactly(target)).as(target).isNotEmpty().allSatisfy(line -> assertThat(line)
                .contains("\"kind\":\"" + kind + "\"", "\"outcome\":\"refused\""));
    }

    // The report names the test's line, not the client library's: the tests' settings file lists the project's own
    // packages, as a project run by the try-it script, which can't name them, would.
    static void assertCallerIs(final Class<?> test, final String target) throws IOException {
        assertThat(ReportLines.aboutExactly(target)).as(target).isNotEmpty().allSatisfy(line -> assertThat(line)
                .contains("\"caller\":\"" + test.getName()));
    }
}
