package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.ExitAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ProcessAdvice;

/**
 * Starting programs, and ending the JVM.
 *
 * <ul>
 * <li>{@code START}: {@code ProcessBuilder.start}, which {@code Runtime.exec} also reaches. From JDK 9 the private
 * {@code start(Redirect[])} is hooked, which {@code start()} and {@code startPipeline} both reach.</li>
 * <li>{@code EXIT}: {@code System.exit} and {@code Runtime.exit}.</li>
 * <li>{@code HALT}: {@code Runtime.halt}.</li>
 * </ul>
 */
public final class ProcessHooks {

    public static final HookSpec START = new HookSpec("java.lang.ProcessBuilder", HookMethods.processStart(),
            ProcessAdvice.class);
    public static final HookSpec EXIT = new HookSpec("java.lang.Runtime", named("exit").and(takesArguments(int.class)),
            ExitAdvice.class);
    public static final HookSpec HALT = new HookSpec("java.lang.Runtime", named("halt").and(takesArguments(int.class)),
            ExitAdvice.class);

    private ProcessHooks() {
    }
}
