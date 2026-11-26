package io.github.fiftieshousewife.airgap.agent;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.application.JUnit4FinishAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.application.JUnit4StartAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.application.SpringEnvironmentAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.application.SpringRefreshAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.application.SpringRunAdvice;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;

// Hooks in the application's own classes, rewritten if and when it loads them, so a JVM without them has nothing to
// check at start; they're proved by the agent's tests on every JDK instead. JUnit 4's RunNotifier, which every JUnit 4
// runner reports through (Surefire's JUnit 4 provider, Gradle's, the Vintage engine, an IDE's), names the test behind
// each call. Spring's environments, its contexts' refresh and Spring Boot's run tell the report which setting and
// profiles a call was made under, and whether Spring was starting; a production profile stops a context starting.
final class ApplicationHooks {

    static final String RUN_NOTIFIER = "org.junit.runner.notification.RunNotifier";

    private ApplicationHooks() {
    }

    static AgentBuilder addTo(final AgentBuilder builder, final ClassFileLocator adviceFiles) {
        return withSpring(withJUnit4(builder, adviceFiles), adviceFiles);
    }

    private static AgentBuilder withJUnit4(final AgentBuilder builder, final ClassFileLocator adviceFiles) {
        final Advice started = Advice.to(JUnit4StartAdvice.class, adviceFiles);
        final Advice finished = Advice.to(JUnit4FinishAdvice.class, adviceFiles);
        return builder.type(named(RUN_NOTIFIER))
                .transform((type, description, loader, module, domain) -> type
                        .visit(started.on(named("fireTestStarted").and(takesArguments(1))))
                        .visit(finished.on(named("fireTestFinished").and(takesArguments(1)))));
    }

    private static AgentBuilder withSpring(final AgentBuilder builder, final ClassFileLocator adviceFiles) {
        final Advice environment = Advice.to(SpringEnvironmentAdvice.class, adviceFiles);
        final Advice refresh = Advice.to(SpringRefreshAdvice.class, adviceFiles);
        final Advice run = Advice.to(SpringRunAdvice.class, adviceFiles);
        return builder
                .type(named("org.springframework.core.env.AbstractEnvironment"))
                .transform((type, description, loader, module, domain) -> type.visit(environment.on(isConstructor())))
                .type(named("org.springframework.context.support.AbstractApplicationContext"))
                .transform((type, description, loader, module, domain) -> type
                        .visit(refresh.on(named("refresh").and(takesArguments(0)))))
                .type(named("org.springframework.boot.SpringApplication"))
                .transform((type, description, loader, module, domain) -> type
                        .visit(run.on(named("run").and(takesArguments(String[].class)).and(not(isStatic())))));
    }
}
