package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class CallersTest {

    private static StackTraceElement frame(final String className) {
        return new StackTraceElement(className, "run", className + ".java", 7);
    }

    private static final StackTraceElement[] STACK = {
        frame("io.github.fiftieshousewife.airgap.agent.Gate"),
        frame("java.net.Socket"),
        frame("org.postgresql.core.PGStream"),
        frame("com.example.orders.OrderRepository"),
        frame("org.junit.platform.Engine"),
    };

    @Test
    void namesTheFirstFrameOutsideTheJdkAndTheAgent() {
        assertThat(new Callers(List.of()).caller(STACK)).startsWith("org.postgresql.core.PGStream.run");
    }

    @Test
    void namesTheProjectsOwnFrameWhenItsPackagesAreGiven() {
        assertAll(
                () -> assertThat(new Callers(List.of("com.example.")).caller(STACK))
                        .startsWith("com.example.orders.OrderRepository.run"),
                () -> assertThat(new Callers(List.of("org.nowhere.")).caller(STACK)).isEqualTo("(unknown)"),
                () -> assertThat(new Callers(List.of()).caller(frame("java.lang.Thread"))).isEqualTo("(unknown)"));
    }

    @Test
    void trimsTheStackWithoutTheAgentsOwnFrames() {
        final StackTraceElement[] deep = new StackTraceElement[30];
        java.util.Arrays.fill(deep, frame("com.example.Deep"));
        deep[0] = frame("io.github.fiftieshousewife.airgap.agent.Decider");

        assertAll(
                () -> assertThat(new Callers(List.of()).trimmed(STACK)).hasSize(4)
                        .noneMatch(line -> line.contains(".agent.")),
                () -> assertThat(new Callers(List.of()).trimmed(deep)).hasSize(12));
    }
}
