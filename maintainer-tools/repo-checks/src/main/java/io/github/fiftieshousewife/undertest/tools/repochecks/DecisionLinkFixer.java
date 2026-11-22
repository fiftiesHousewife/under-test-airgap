package io.github.fiftieshousewife.undertest.tools.repochecks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Re-points every reference to a decision record at its current file, matching by name, so records can be renumbered
 * or moved between docs/decisions/ and decision-guides/ without breaking links. Markdown links are made relative to the
 * linking file; paths in YAML files are written relative to the repository root.
 */
public final class DecisionLinkFixer {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)#\\s]*?)\\d{4}-([a-z0-9-]+)\\.md");
    private static final Pattern YAML_PATH =
            Pattern.compile("(?:docs/decisions|decision-guides)/\\d{4}-([a-z0-9-]+)\\.md");

    /** Where each decision record currently is, by name, and the repository root YAML paths are relative to. */
    record Records(Path root, Map<String, Path> byName) {
    }

    /** Fixes every file that needs it and returns how many files changed. */
    public int fix(final RepoFiles files) {
        final Records records = new Records(files.root(), currentRecords(files.root()));
        int changed = 0;
        for (final Path file : files.withExtension(".md", ".yaml", ".yml")) {
            final String text = read(file);
            final String fixed = fixText(file, text, records);
            if (!fixed.equals(text)) {
                write(file, fixed);
                changed++;
            }
        }
        return changed;
    }

    String fixText(final Path file, final String text, final Records records) {
        final Map<String, Path> byName = records.byName();
        if (String.valueOf(file.getFileName()).endsWith(".md")) {
            return replace(MARKDOWN_LINK, text, match -> byName.containsKey(match.group(2))
                    ? "](" + slashes(file.resolveSibling(".").normalize().relativize(byName.get(match.group(2))))
                    : match.group(0));
        }
        return replace(YAML_PATH, text, match -> byName.containsKey(match.group(1))
                ? slashes(records.root().relativize(byName.get(match.group(1))))
                : match.group(0));
    }

    private static String slashes(final Path path) {
        return path.toString().replace('\\', '/');
    }

    static Map<String, Path> currentRecords(final Path root) {
        final Map<String, Path> records = new HashMap<>();
        for (final String folder : RepoChecks.DECISION_FOLDERS) {
            final Path dir = root.resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.map(Path::getFileName).map(Path::toString)
                        .filter(name -> name.matches("\\d{4}-[a-z0-9-]+\\.md"))
                        .forEach(name -> records.put(name.substring(5, name.length() - 3), dir.resolve(name)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return records;
    }

    private static String replace(final Pattern pattern, final String text,
                                  final java.util.function.Function<Matcher, String> replacement) {
        final Matcher matcher = pattern.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.apply(matcher)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(final Path file, final String text) {
        try {
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
