package io.github.fiftieshousewife.airgap.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Works out, from a stack trace, which code made a risky call: the first frame that belongs to the project, not to
 * the JDK, the agent, the test runner or the build tool.
 */
public final class Callers {

    private static final int STACK_FRAMES = 12;
    private static final List<String> NOT_THE_PROJECT = Collections.unmodifiableList(Arrays.asList(
            "java.", "javax.", "jdk.", "sun.", "com.sun.", "io.github.fiftieshousewife.airgap.", "org.junit.",
            "junit.", "org.gradle.", "worker.org.gradle.", "org.apache.maven.surefire.", "net.bytebuddy."));

    private final List<String> project;

    /**
     * Callers worked out for a project.
     *
     * @param project package prefixes of the project's own code; if empty, the first frame outside the JDK, the
     *                agent, the test runner and the build tool counts as the project's
     */
    public Callers(final List<String> project) {
        this.project = Collections.unmodifiableList(new ArrayList<>(project));
    }

    /**
     * The frame that made the call.
     *
     * @param frames the stack, innermost first
     * @return the frame, as {@code class.method(File.java:line)}, or {@code (unknown)}
     */
    public String caller(final StackTraceElement... frames) {
        for (final StackTraceElement frame : frames) {
            if (isProject(frame.getClassName())) {
                return frame.toString();
            }
        }
        return "(unknown)";
    }

    /**
     * The stack, without the agent's own frames, cut to a readable length.
     *
     * @param frames the stack, innermost first
     * @return up to twelve frames, innermost first
     */
    public List<String> trimmed(final StackTraceElement... frames) {
        final List<String> kept = new ArrayList<>();
        for (final StackTraceElement frame : frames) {
            if (!frame.getClassName().startsWith("io.github.fiftieshousewife.airgap.agent.")
                    && kept.size() < STACK_FRAMES) {
                kept.add(frame.toString());
            }
        }
        return kept;
    }

    private boolean isProject(final String className) {
        if (!project.isEmpty()) {
            for (final String prefix : project) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
        for (final String prefix : NOT_THE_PROJECT) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }
}
