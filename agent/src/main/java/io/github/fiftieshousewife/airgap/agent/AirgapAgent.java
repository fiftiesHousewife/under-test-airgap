package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.jar.JarFile;

/**
 * The agent's entry point, named in the jar's manifest ({@code -javaagent:airgap-agent.jar=...}).
 *
 * <p>The JDK's own classes, which the hooks rewrite, can only see classes the bootstrap class loader loads. So this
 * class first adds the agent's jar to the bootstrap class path, then hands over to {@link AgentStart}, loaded from
 * there. Nothing else in the agent may be loaded before that, or the JVM would hold two copies of it.</p>
 */
public final class AirgapAgent {

    private static final String START = "io.github.fiftieshousewife.airgap.agent.AgentStart";

    private AirgapAgent() {
    }

    /**
     * Called by the JVM before the program's {@code main}.
     *
     * @param arguments the text after {@code =} on the command line; see {@link Settings}
     * @param instrumentation the JVM's instrumentation, used to rewrite the JDK's classes
     * @throws IOException if the agent's jar can't be opened
     * @throws URISyntaxException if the agent's jar has no usable location
     * @throws ReflectiveOperationException if the agent's start can't be found; if the agent can't start, the JVM
     *         stops, rather than running unguarded
     */
    public static void premain(final String arguments, final Instrumentation instrumentation)
            throws IOException, URISyntaxException, ReflectiveOperationException {
        onTheBootstrapClassPath(instrumentation);
        final Class<?> start = Class.forName(START, true, null);
        try {
            start.getMethod("start", String.class, Instrumentation.class).invoke(null, arguments, instrumentation);
        } catch (InvocationTargetException e) {
            throw startFailure(e);
        }
    }

    // Named with -Xbootclasspath/a as well, the jar is on the bootstrap class path from the start, and HotSpot's
    // class-data sharing has no warning to print about it being added later.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The path is the agent's own jar, from its code source, not from any input")
    private static void onTheBootstrapClassPath(final Instrumentation instrumentation)
            throws IOException, URISyntaxException {
        if (AirgapAgent.class.getClassLoader() != null) {
            final File jar = new File(AirgapAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar));
        }
    }

    // The start-up's own failure, as itself, so the JVM reports why the agent couldn't start.
    @SuppressFBWarnings(value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
            justification = "The agent's own failure to start is passed on unchanged, so the JVM shows why it stopped")
    private static ReflectiveOperationException startFailure(final InvocationTargetException failed) {
        final Throwable cause = failed.getCause();
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return failed;
    }
}
