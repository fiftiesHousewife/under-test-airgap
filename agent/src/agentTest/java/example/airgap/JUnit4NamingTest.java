package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

// With the agent attached and no listener of airgap's, JUnit 4 run by itself names each test, through the agent's
// hook in JUnit 4's RunNotifier: as Surefire 2.19 runs it, which ignores custom listeners.
class JUnit4NamingTest {

    static final List<String> SEEN = Collections.synchronizedList(new ArrayList<>());

    public static class OldStyle {

        @org.junit.Test
        public void looksSomethingUp() {
            org.junit.Assert.assertTrue(SEEN.add(Gate.currentTest()));
        }
    }

    @Test
    void namesEachJUnit4TestWhileItRunsAndNothingAfter() {
        SEEN.clear();

        final Result result = new JUnitCore().run(OldStyle.class);

        assertAll(
                () -> assertThat(result.wasSuccessful()).isTrue(),
                () -> assertThat(SEEN).containsExactly(OldStyle.class.getName() + ".looksSomethingUp"),
                () -> assertThat(Gate.currentTest()).as("after JUnit 4 has finished").isNull());
    }
}
