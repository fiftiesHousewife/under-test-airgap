package io.github.fiftieshousewife.airgap.tools.repochecks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The repository's own files that the checks look at, skipping build output, working notes and installed AI assistant
 * skills ({@code .claude/}: external skills installed by APM, and links to this repo's own skills, which are checked
 * where they live, in {@code .github/skills/}).
 */
public record RepoFiles(Path root) {

    private static final Set<String> SKIPPED = Set.of("build", ".gradle", ".git", ".idea", "node_modules",
            "research_notes", "reports", "apm_modules", ".kotlin", ".claude");

    public List<Path> withExtension(final String... extensions) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::notSkipped)
                    .filter(path -> hasExtension(path, extensions))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean notSkipped(final Path path) {
        final Path relative = root.relativize(path);
        for (final Path part : relative) {
            if (SKIPPED.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasExtension(final Path path, final String... extensions) {
        final String name = String.valueOf(path.getFileName());
        for (final String extension : extensions) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
