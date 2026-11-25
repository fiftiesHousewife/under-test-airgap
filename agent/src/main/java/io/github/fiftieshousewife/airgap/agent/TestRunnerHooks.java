package io.github.fiftieshousewife.airgap.agent;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.JUnit4FinishAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JUnit4StartAdvice;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;

// Hooks in test runners, which name the test behind each call however the tests are run: JUnit 4's RunNotifier, which
// every JUnit 4 runner reports through (Surefire's JUnit 4 provider, Gradle's, the JUnit Platform's Vintage engine, an
// IDE's). Unlike the JDK's hooks, these are in the application's own classes, rewritten if and when it loads them, so
// a JVM without JUnit 4 has nothing to check at start; they're proved by the agent's tests on every JDK instead.
final class TestRunnerHooks {

    static final String RUN_NOTIFIER = "org.junit.runner.notification.RunNotifier";

    private TestRunnerHooks() {
    }

    static AgentBuilder addTo(final AgentBuilder builder, final ClassFileLocator adviceFiles) {
        final Advice started = Advice.to(JUnit4StartAdvice.class, adviceFiles);
        final Advice finished = Advice.to(JUnit4FinishAdvice.class, adviceFiles);
        return builder.type(named(RUN_NOTIFIER))
                .transform((type, description, loader, module, domain) -> type
                        .visit(started.on(named("fireTestStarted").and(takesArguments(1))))
                        .visit(finished.on(named("fireTestFinished").and(takesArguments(1)))));
    }
}
