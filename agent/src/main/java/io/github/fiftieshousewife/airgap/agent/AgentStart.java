package io.github.fiftieshousewife.airgap.agent;

import java.io.UnsupportedEncodingException;
import java.lang.instrument.Instrumentation;

/**
 * Starts the agent, once {@link AirgapAgent} has put the jar on the bootstrap class path: reads the settings, opens
 * the gate, installs the hooks, and checks every hook is in place. Loaded by the bootstrap class loader.
 */
public final class AgentStart {

    private AgentStart() {
    }

    /**
     * Starts the agent.
     *
     * @param arguments the agent's settings, as given on the command line
     * @param instrumentation the JVM's instrumentation
     * @throws UnsupportedEncodingException never: UTF-8 is always supported
     */
    public static void start(final String arguments, final Instrumentation instrumentation)
            throws UnsupportedEncodingException {
        final Settings settings = Settings.parse(arguments);
        if (settings.getMode() == Mode.OFF) {
            return;
        }
        final JsonLinesReport report = new JsonLinesReport(settings.getReport());
        report.checkWritable();
        Gate.open(new Decider(settings, report));
        SelfCheck.run(Hooks.install(instrumentation, settings.getWithoutHooks()), settings.getWithoutHooks());
    }
}
