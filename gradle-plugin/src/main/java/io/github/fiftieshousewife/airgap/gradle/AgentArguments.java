package io.github.fiftieshousewife.airgap.gradle;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * The {@code -javaagent:} arguments for one guarded task, worked out when the task runs, so settings made after the
 * plugin is applied still count. The project's settings file, {@code testing/airgap.yaml}, is named to the agent when
 * it's there, and its content is one of the task's inputs. So is the build's root folder, inside which writes go ahead
 * unreported. The report's, the settings file's and the root's paths are left out of the task's inputs, so moving the
 * project doesn't make the task run again.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP",
        justification = "Gradle reads and sets these properties through the getters, so they return the live objects")
@Builder
public final class AgentArguments implements CommandLineArgumentProvider {

    private final FileCollection agent;
    private final Property<String> mode;
    /** Lets a JVM attach an agent to itself, from inside, with no second program. */
    public static final String ALLOW_ATTACH_SELF = "-Djdk.attach.allowAttachSelf=true";

    private final ListProperty<String> allow;
    private final ListProperty<String> packages;
    private final String run;
    private final Provider<RegularFile> report;
    private final FileCollection settingsFile;
    /** The build's root folder, inside which writes go ahead unreported. */
    private final File rootDir;

    /** @return the agent's jar */
    @Classpath
    public FileCollection getAgent() {
        return agent;
    }

    /** @return the agent's settings, without the report's path */
    @Input
    public String getSettings() {
        final List<String> settings = new ArrayList<>();
        if (mode.isPresent()) {
            settings.add("mode=" + mode.get());
        }
        settings.add("run=" + run);
        add(settings, "allow", allow);
        add(settings, "project", packages);
        return String.join(";", settings);
    }

    /** @return the project's settings file, which may not be there */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getSettingsFile() {
        return settingsFile;
    }

    /** @return the build's root folder; left out of the inputs, so moving the build doesn't run the task again */
    @Internal
    public File getRootDir() {
        return rootDir;
    }

    /** @return where the report is written */
    @Internal
    public Provider<RegularFile> getReport() {
        return report;
    }

    /**
     * The jar goes on the bootstrap class path from the start as well as being named as the agent: added later, by the
     * agent, it makes HotSpot 17 and later print a class-data sharing warning in every JVM. Test JVMs may attach agents
     * to themselves, as Mockito's inline mock maker does: otherwise, on JDK 9 and later, Byte Buddy starts a second
     * {@code java} program to attach from outside, which the agent refuses.
     */
    @Override
    public Iterable<String> asArguments() {
        final String jar = agent.getSingleFile().getAbsolutePath();
        final File file = settingsFile.getSingleFile();
        final String named = file.isFile() ? "settings=" + file.getAbsolutePath() + ";" : "";
        final List<String> arguments = new ArrayList<>(Arrays.asList("-Xbootclasspath/a:" + jar,
                "-javaagent:" + jar + "=report=" + reportFile().getAbsolutePath() + ";root="
                        + rootDir.getAbsolutePath() + ";" + named + getSettings()));
        if ("test".equals(run)) {
            arguments.add(ALLOW_ATTACH_SELF);
        }
        return arguments;
    }

    private File reportFile() {
        return report.get().getAsFile();
    }

    private static void add(final List<String> settings, final String key, final ListProperty<String> values) {
        final List<String> list = values.getOrElse(Collections.<String>emptyList());
        if (!list.isEmpty()) {
            settings.add(key + "=" + String.join(",", list));
        }
    }
}
