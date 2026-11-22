package io.github.fiftieshousewife.airgap.agent;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.Nexus;

/**
 * Installs the hooks: rewrites the JDK's classes, those already loaded and those loaded later, so each risky method
 * calls {@link Gate} before it does anything else. Any class that can't be rewritten is kept, with why, for the
 * self-check's message.
 */
final class Hooks {

    private Hooks() {
    }

    static List<String> install(final Instrumentation instrumentation, final Set<String> without)
            throws UnsupportedEncodingException {
        final ByteArrayOutputStream errorText = new ByteArrayOutputStream();
        final PrintStream errorStream = new PrintStream(errorText, true, "UTF-8");
        // The advice classes are loaded from the bootstrap class path, which Byte Buddy can't read class files from;
        // the agent's jar is also on the system class path, so their class files are read from there.
        final ClassFileLocator adviceFiles = ClassFileLocator.ForClassLoader.ofSystemLoader();
        // Byte Buddy's nexus, which the hooks never use, reaches for sun.misc.Unsafe when a builder is made, and JDK 24
        // and later print a warning about it. The property's name is shaded, so it turns off only the agent's own copy.
        System.setProperty(Nexus.PROPERTY, Boolean.TRUE.toString());
        AgentBuilder builder = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.StreamWriting(errorStream).withErrorsOnly())
                .ignore(nameStartsWith("io.github.fiftieshousewife.airgap."))
                .assureReadEdgeTo(instrumentation, Gate.class);
        for (final Hook hook : Hook.values()) {
            if (!without.contains(hook.id())) {
                final Advice advice = Advice.to(hook.getAdvice(), adviceFiles);
                builder = builder.type(named(hook.getTypeName()))
                        .transform((type, description, loader, module, domain) ->
                                type.visit(advice.on(hook.getMethods())));
            }
        }
        loadHookedClasses();
        builder.installOn(instrumentation);
        return errorLines(new String(errorText.toByteArray(), StandardCharsets.UTF_8));
    }

    private static List<String> errorLines(final String text) {
        final List<String> lines = new ArrayList<>();
        for (final String line : text.split("\\R")) {
            if (line.startsWith("[Byte Buddy] ERROR")) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Loads every class the hooks rewrite before installing them, so each is rewritten once it's loaded rather than
     * while it loads; rewriting while loading can make Byte Buddy need the class it's in the middle of loading.
     */
    private static void loadHookedClasses() {
        for (final Hook hook : Hook.values()) {
            try {
                Class.forName(hook.getTypeName(), false, null);
            } catch (ClassNotFoundException notOnThisJdk) {
                // The self-check reports the hook as missing.
                continue;
            }
        }
    }
}
