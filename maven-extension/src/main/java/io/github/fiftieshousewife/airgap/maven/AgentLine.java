package io.github.fiftieshousewife.airgap.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The JVM arguments that start a run with the agent: the jar on the bootstrap class path as well as named as the agent
 * (so HotSpot 17 and later print no class-data sharing warning), its settings, and, for tests, leave to attach agents
 * to the JVM itself, as Mockito's inline mock maker does, so it starts no {@code java} program the agent would refuse.
 */
final class AgentLine {

    /** Lets a JVM attach an agent to itself, from inside, with no second program. */
    static final String ALLOW_ATTACH_SELF = "-Djdk.attach.allowAttachSelf=true";

    private AgentLine() {
    }

    /**
     * @param agent the agent's jar
     * @param run where the run reports, and its settings
     * @return the arguments, each on its own
     */
    static List<String> arguments(final File agent, final GuardedRun run) {
        final List<String> arguments = new ArrayList<>();
        final String jar = agent.getAbsolutePath();
        arguments.add("-Xbootclasspath/a:" + jar);
        final StringBuilder settings = new StringBuilder("-javaagent:").append(jar)
                .append("=report=").append(run.getReport().getAbsolutePath())
                .append(";root=").append(run.getRoot().getAbsolutePath());
        if (run.getSettings() != null) {
            settings.append(";settings=").append(run.getSettings().getAbsolutePath());
        }
        arguments.add(settings.append(";run=").append(run.getRun()).toString());
        if ("test".equals(run.getRun())) {
            arguments.add(ALLOW_ATTACH_SELF);
        }
        return arguments;
    }

    /**
     * The arguments as one line, as Surefire's {@code argLine} and Spring Boot's {@code jvmArguments} take them: each
     * in double quotes if it has a space, as a path may.
     *
     * @param agent the agent's jar
     * @param run where the run reports, and its settings
     * @return the line
     */
    static String line(final File agent, final GuardedRun run) {
        final StringBuilder line = new StringBuilder();
        for (final String argument : arguments(agent, run)) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(argument.indexOf(' ') < 0 ? argument : '"' + argument + '"');
        }
        return line.toString();
    }
}
