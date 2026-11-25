package io.github.fiftieshousewife.airgap.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/**
 * With the agent attached, JUnit 4 run by itself, as Surefire's JUnit 4 provider runs it, names each test. The JUnit 4
 * classes inside are run by JUnit 4 with the listener, not by JUnit Jupiter, and check what they see themselves.
 */
class AirgapRunListenerTest {

    public static class OldStyle {

        @org.junit.Test
        public void looksSomethingUp() {
            Assert.assertEquals(OldStyle.class.getName() + ".looksSomethingUp", Gate.currentTest());
        }

        @AllowCalls("run-listener.example.invalid")
        public static class Allowed {

            @org.junit.Test
            public void isAllowedItsStandIn() {
                String outcome = "resolved";
                try {
                    InetAddress.getByName("run-listener.example.invalid");
                } catch (UnknownHostException expected) {
                    outcome = expected.getMessage();
                }
                Assert.assertFalse("allowed, so not refused: " + outcome,
                        outcome.contains("refused by the airgap agent"));
            }
        }
    }

    private static Result runWithTheListener(final Class<?> tests) {
        final JUnitCore junit = new JUnitCore();
        junit.addListener(new AirgapRunListener());
        return junit.run(tests);
    }

    @Test
    void namesEachTestWhileItRunsAndNothingAfter() {
        final Result result = runWithTheListener(OldStyle.class);

        assertAll(
                () -> assertThat(result.getFailures()).isEmpty(),
                () -> assertThat(result.getRunCount()).isEqualTo(1),
                () -> assertThat(Gate.currentTest()).isEqualTo(AirgapRunListenerTest.class.getName()
                        + ".namesEachTestWhileItRunsAndNothingAfter"));
    }

    @Test
    void givesEachTestItsClassesAllowances() {
        final Result result = runWithTheListener(OldStyle.Allowed.class);

        assertThat(result.getFailures()).as("the lookup isn't refused, since the class allows it").isEmpty();
    }

    @Test
    void namesATestWithNoMethodByItsDisplayName() {
        assertThat(AirgapRunListener.name(Description.createSuiteDescription("a suite"))).isEqualTo("a suite");
    }
}
