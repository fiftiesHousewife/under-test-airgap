package example.billing;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import org.junit.Test;

// The job's own tests, JUnit 4: under the agent, each step that reaches outside the project fails as it would on a
// locked-down machine, and the report lists it.
public class NightlyBillingTest {

    private final NightlyBilling billing = new NightlyBilling();

    @Test
    public void savesTheInvoice() {
        assertRefused(() -> billing.bill("acme"));
    }

    @Test
    public void printsTheInvoice() {
        assertRefused(() -> billing.print("acme"));
    }

    @Test
    public void mailsTheInvoice() {
        assertRefused(() -> billing.mail("acme@example.invalid"));
    }

    private interface Step {
        void run() throws IOException;
    }

    private static void assertRefused(final Step step) {
        try {
            step.run();
            fail("the step went ahead");
        } catch (IOException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("refused by the airgap agent"));
        }
    }
}
