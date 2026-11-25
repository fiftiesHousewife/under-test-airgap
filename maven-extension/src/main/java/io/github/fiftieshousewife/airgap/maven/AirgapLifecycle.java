package io.github.fiftieshousewife.airgap.maven;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The extension Maven calls: once it has read every module's pom, it adds the agent to each module's test and
 * application runs, in memory, and when the build ends it names each report that has calls in it. Maven finds it
 * through {@code META-INF/sisu/javax.inject.Named}.
 */
@Named("airgap")
@Singleton
public final class AirgapLifecycle extends AbstractMavenLifecycleParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(AirgapLifecycle.class);

    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
        final Guarding guarding;
        try {
            final ClassLoader loader = AirgapLifecycle.class.getClassLoader();
            guarding = Guarding.builder()
                    .agent(Jars.find(loader, Jars.AGENT_CLASS, "airgap-agent"))
                    .listener(Jars.find(loader, Jars.LISTENER_CLASS, "airgap-junit"))
                    .root(root(session))
                    .userProperties(userProperties(session))
                    .build();
        } catch (IllegalStateException missing) {
            throw new MavenExecutionException(missing.getMessage(), missing);
        }
        for (final MavenProject project : session.getProjects()) {
            TestPlugins.guard(project, guarding);
            RunPlugins.guard(project, guarding, session.getGoals());
        }
    }

    @Override
    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "CRLF_INJECTION_LOGS"},
            justification = "The folders come from Maven's own model of the build, and the names from its poms")
    public void afterSessionEnd(final MavenSession session) {
        for (final MavenProject project : session.getProjects()) {
            final File[] runs = new File(project.getBuild().getDirectory(), "airgap").listFiles(File::isDirectory);
            for (final File run : runs == null ? new File[0] : runs) {
                final File report = new File(run, "calls.jsonl");
                if (report.isFile()) {
                    LOG.info("airgap: {} made risky calls; see {}", project.getArtifactId(), report);
                }
            }
        }
    }

    /** The build's root folder: the top-level project's, or else the folder Maven was started in. */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The paths come from Maven's own model of the build being run, only read")
    private static File root(final MavenSession session) {
        final MavenProject top = session.getTopLevelProject();
        return top != null && top.getBasedir() != null ? top.getBasedir()
                : new File(session.getExecutionRootDirectory());
    }

    private static Properties userProperties(final MavenSession session) {
        final Properties given = session.getUserProperties();
        return given == null ? new Properties() : given;
    }
}
