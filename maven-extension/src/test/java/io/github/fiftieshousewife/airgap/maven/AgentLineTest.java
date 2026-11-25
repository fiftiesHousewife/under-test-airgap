package io.github.fiftieshousewife.airgap.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import org.junit.jupiter.api.Test;

@SuppressFBWarnings(value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
        justification = "The paths are made up, to compare as text; nothing opens them")
class AgentLineTest {

    private static GuardedRun run(final File settings, final String kind) {
        return GuardedRun.builder().report(new File("/work/orders/target/airgap/x/calls.jsonl"))
                .root(new File("/work/orders")).settings(settings).run(kind).build();
    }

    @Test
    void startsTheJvmWithTheAgentAndItsSettings() {
        final String jar = Sample.AGENT.getAbsolutePath();

        assertAll(
                () -> assertThat(AgentLine.arguments(Sample.AGENT, run(new File("/work/orders/testing/airgap.yaml"),
                        "test"))).containsExactly("-Xbootclasspath/a:" + jar,
                        "-javaagent:" + jar + "=report=" + new File("/work/orders/target/airgap/x/calls.jsonl")
                                .getAbsolutePath() + ";root=" + new File("/work/orders").getAbsolutePath()
                                + ";settings=" + new File("/work/orders/testing/airgap.yaml").getAbsolutePath()
                                + ";run=test", AgentLine.ALLOW_ATTACH_SELF),
                () -> assertThat(AgentLine.arguments(Sample.AGENT, run(null, "application"))).hasSize(2)
                        .last().asString().endsWith(";run=application").doesNotContain("settings="));
    }

    @Test
    void quotesAnArgumentWithASpaceInIt() {
        final String line = AgentLine.line(new File("/My Tools/airgap-agent.jar"), run(null, "application"));

        assertThat(line).startsWith("\"-Xbootclasspath/a:" + new File("/My Tools/airgap-agent.jar").getAbsolutePath()
                + "\" \"-javaagent:");
    }
}
