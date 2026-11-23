package io.github.fiftieshousewife.airgap.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The report's summary ({@code schema/airgap-summary.schema.json}), beside it as {@code <name>-summary.json}: each
 * distinct kind and target once, with how many calls there were, how they ended, and the tests and lines of code
 * that made them. It is the list of everything the project's code reaches, for a person to confirm and give
 * stand-ins.
 */
final class Summary {

    private Summary() {
    }

    /**
     * Where a report's summary goes.
     *
     * @param report the report's file
     * @return {@code calls-summary.json} beside {@code calls.jsonl}; for any other name, {@code -summary.json} added
     */
    static Path fileFor(final Path report) {
        final String name = String.valueOf(report.getFileName());
        final String stem = name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
        return report.resolveSibling(stem + "-summary.json");
    }

    /**
     * Summarises a report.
     *
     * @param name the report's file name, as the summary names it
     * @param report the report's lines; lines that aren't whole calls are skipped
     * @return the summary, one target to a line, in order of kind and then target
     */
    static String of(final String name, final String report) {
        final Map<String, Target> targets = new TreeMap<>();
        for (final String line : report.split("\n")) {
            final Map<String, String> fields = JsonFields.of(line);
            final String kind = fields.get("kind");
            final String target = fields.get("target");
            if (kind != null && target != null && fields.containsKey("outcome")) {
                targets.computeIfAbsent(kind + '\u0000' + target, key -> new Target(kind, target)).add(fields);
            }
        }
        final StringBuilder out = new StringBuilder("{\"report\":").append(Json.quote(name)).append(",\"targets\":[");
        String separator = "\n";
        for (final Target target : targets.values()) {
            out.append(separator).append(target.toJson());
            separator = ",\n";
        }
        return out.append("\n]}\n").toString();
    }

    /** One distinct target, and what reached it. */
    private static final class Target {

        private final String kind;
        private final String target;
        private long calls;
        private final Map<String, Long> outcomes = new TreeMap<>();
        private final Set<String> tests = new TreeSet<>();
        private final Set<String> callers = new TreeSet<>();

        Target(final String kind, final String target) {
            this.kind = kind;
            this.target = target;
        }

        void add(final Map<String, String> call) {
            calls++;
            outcomes.merge(call.get("outcome"), 1L, Long::sum);
            addIfPresent(tests, call.get("test"));
            addIfPresent(callers, call.get("caller"));
        }

        private static void addIfPresent(final Set<String> values, final String value) {
            if (value != null) {
                values.add(value);
            }
        }

        Json toJson() {
            final Json ended = new Json();
            outcomes.forEach(ended::number);
            return new Json().field("kind", kind).field("target", target).number("calls", calls)
                    .object("outcomes", ended).array("tests", new ArrayList<>(tests))
                    .array("callers", new ArrayList<>(callers));
        }
    }
}
