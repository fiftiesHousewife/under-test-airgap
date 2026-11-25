package io.github.fiftieshousewife.airgap.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/** With the agent attached, JUnit 4 run by itself, as Surefire's JUnit 4 provider runs it, names each test. */
class AirgapRunListenerTest {

    /** What each JUnit 4 test saw while it ran. */
    static final List<String> SEEN = Collections.synchronizedList(new ArrayList<>());

    /** A JUnit 4 test class, run by JUnit 4 with the listener; JUnit Jupiter doesn't run it itself. */
    public static class OldStyle {

        @org.junit.Test
        public void looksSomethingUp() {
            SEEN.add("running: " + Gate.currentTest());
        }

        @AllowCalls("run-listener.example.invalid")
        public static class Allowed {

            @org.junit.Test
            public void isAllowedItsStandIn() {
                try {
                    InetAddress.getByName("run-listener.example.invalid");
                } catch (UnknownHostException expected) {
                    SEEN.add("failed: " + expected.getMessage());
                }
            }
        }
    }

    @Test
    void namesEachTestWhileItRunsAndNothingAfter() {
        SEEN.clear();
        final JUnitCore junit = new JUnitCore();
        junit.addListener(new AirgapRunListener());

        final Result result = junit.run(OldStyle.class);

        assertAll(
                () -> assertThat(result.wasSuccessful()).isTrue(),
                () -> assertThat(SEEN).containsExactly("running: " + OldStyle.class.getName() + ".looksSomethingUp"),
                () -> assertThat(Gate.currentTest()).isEqualTo(AirgapRunListenerTest.class.getName()
                        + ".namesEachTestWhileItRunsAndNothingAfter"));
    }

    @Test
    void givesEachTestItsClassesAllowances() {
        SEEN.clear();
        final JUnitCore junit = new JUnitCore();
        junit.addListener(new AirgapRunListener());

        junit.run(OldStyle.Allowed.class);

        assertAll(
                () -> assertThat(SEEN).as("the lookup of a name that can't resolve fails").hasSize(1),
                () -> assertThat(SEEN).as("as unresolvable, not refused, since the class allows it")
                        .noneMatch(seen -> seen.contains("refused by the airgap agent")));
    }

    @Test
    void namesATestWithNoMethodByItsDisplayName() {
        assertThat(AirgapRunListener.name(Description.createSuiteDescription("a suite"))).isEqualTo("a suite");
    }
}
