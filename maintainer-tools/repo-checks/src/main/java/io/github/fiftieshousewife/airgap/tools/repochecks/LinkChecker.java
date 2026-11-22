package io.github.fiftieshousewife.airgap.tools.repochecks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds Markdown links to files in this repository that don't exist. */
public final class LinkChecker {

    private static final Pattern LINK = Pattern.compile("\\]\\(([^)#\\s]+)");

    public List<Problem> check(final RepoFiles files) {
        final List<Problem> problems = new ArrayList<>();
        for (final Path file : files.withExtension(".md")) {
            problems.addAll(checkFile(file));
        }
        return problems;
    }

    List<Problem> checkFile(final Path file) {
        final List<Problem> problems = new ArrayList<>();
        final Matcher matcher = LINK.matcher(read(file));
        while (matcher.find()) {
            final String target = matcher.group(1);
            if (isLocal(target) && !Files.exists(file.resolveSibling(target).normalize())) {
                problems.add(new Problem(file, "broken link to " + target));
            }
        }
        return problems;
    }

    private static boolean isLocal(final String target) {
        return !target.contains("://") && !target.startsWith("mailto:");
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
