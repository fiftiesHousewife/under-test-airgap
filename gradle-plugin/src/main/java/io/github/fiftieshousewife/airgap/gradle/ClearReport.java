package io.github.fiftieshousewife.airgap.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

// Deletes a task's report before the task runs, so the report holds that run's calls only.
final class ClearReport implements Action<Task> {

    private final Provider<RegularFile> report;

    ClearReport(final Provider<RegularFile> report) {
        this.report = report;
    }

    @Override
    public void execute(final Task task) {
        try {
            Files.deleteIfExists(report.get().getAsFile().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("airgap: can't delete the old report " + report.get(), e);
        }
    }
}
