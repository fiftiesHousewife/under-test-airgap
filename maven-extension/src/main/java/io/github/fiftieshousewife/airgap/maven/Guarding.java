package io.github.fiftieshousewife.airgap.maven;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.Properties;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.maven.project.MavenProject;

/** What every guarded run in one build shares: the jars, the build's root folder, and the command line's settings. */
@Getter
@Builder
final class Guarding {

    @NonNull
    private final File agent;
    @NonNull
    private final File listener;
    @NonNull
    private final File root;
    /** The {@code -D} settings given to Maven. */
    @NonNull
    private final Properties userProperties;

    /**
     * Where one run of a module reports, with the module's settings file if it has one.
     *
     * @param project the module
     * @param name the run's name, as its report folder: {@code surefire-default-test}, say
     * @param run {@code test} or {@code application}
     * @return the run
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The paths come from Maven's own model of the build being run, only read")
    GuardedRun run(final MavenProject project, final String name, final String run) {
        final File settings = new File(project.getBasedir(), "testing" + File.separator + "airgap.yaml");
        return GuardedRun.builder()
                .report(new File(new File(new File(project.getBuild().getDirectory(), "airgap"), name), "calls.jsonl"))
                .root(root)
                .settings(settings.isFile() ? settings : null)
                .run(run)
                .build();
    }

    /** @return the arguments that start a run with the agent, each on its own */
    java.util.List<String> arguments(final GuardedRun run) {
        return AgentLine.arguments(agent, run);
    }

    /** @return the arguments that start a run with the agent, as one line */
    String line(final GuardedRun run) {
        return AgentLine.line(agent, run);
    }
}
