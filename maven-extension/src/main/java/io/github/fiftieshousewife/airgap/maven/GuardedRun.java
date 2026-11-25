package io.github.fiftieshousewife.airgap.maven;

import java.io.File;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/** One run of a module's code under the agent: a test plugin's execution, or an application start. */
@Getter
@Builder
final class GuardedRun {

    /** Where its report goes: {@code target/airgap/<name>/calls.jsonl}. */
    @NonNull
    private final File report;
    /** The build's root folder, inside which writes go ahead unreported. */
    @NonNull
    private final File root;
    /** The module's {@code testing/airgap.yaml}, or {@code null} if it has none. */
    private final File settings;
    /** The kind of JVM, for the report: {@code test} or {@code application}. */
    @NonNull
    private final String run;
}
