package io.github.fiftieshousewife.airgap.maven;

import java.io.File;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

// One run of a module's code under the agent: a test plugin's execution, or an application start. Its report goes to
// target/airgap/<name>/calls.jsonl; root is the build's root folder, inside which writes go ahead unreported; settings
// is the module's testing/airgap.yaml, or null if it has none; run is the kind of JVM, for the report: test or
// application.
@Getter
@Builder
final class GuardedRun {

    @NonNull
    private final File report;
    @NonNull
    private final File root;
    private final File settings;
    @NonNull
    private final String run;
}
