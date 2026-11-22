package io.github.fiftieshousewife.undertest.tools.repochecks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The parts of one decision record the index needs, read from its front matter and title. */
public record DecisionRecord(Path file, String title, Metadata metadata) {

    /** Front matter fields shown in the index. */
    public record Metadata(String status, String decisionMakers, String group) {
    }

    private static final Pattern TITLE = Pattern.compile("(?m)^# (.+)$");
    private static final Pattern STATUS_LINE = Pattern.compile("(?m)\\*\\*Status:\\*\\* (.+)$");

    public static DecisionRecord read(final Path file) {
        final String text = readText(file);
        final Matcher statusLine = STATUS_LINE.matcher(text);
        final String status = statusLine.find() ? statusLine.group(1).trim() : field(text, "status");
        final Metadata metadata = new Metadata(status, field(text, "decision-makers"), field(text, "group"));
        final Matcher title = TITLE.matcher(text);
        if (!title.find()) {
            throw new IllegalStateException(file + " has no title");
        }
        return new DecisionRecord(file, title.group(1).trim(), metadata);
    }

    private static String field(final String text, final String name) {
        final Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(name) + ":\\s*(.+)$").matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("front matter has no " + name);
        }
        return matcher.group(1).trim();
    }

    private static String readText(final Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
