package io.github.fiftieshousewife.airgap.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

// The report's summary (schema/airgap-summary.schema.json), beside it as <name>-summary.json: each
// distinct kind and target once, with how many calls there were, how they ended, and the tests and lines of code
// that made them. It is the list of everything the project's code reaches, for a person to confirm and give
// stand-ins. A random id, in an unpacked library's or program's name, is a UUID or a run of eight or more digits.
final class Summary {

    private static final Pattern RANDOM_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9]{8,}");

    private Summary() {
    }

    // Where a report's summary goes.
    //
    // report: the report's file
    // Returns calls-summary.json beside calls.jsonl; for any other name, -summary.json added
    static Path fileFor(final Path report) {
        final String name = String.valueOf(report.getFileName());
        final String stem = name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
        return report.resolveSibling(stem + "-summary.json");
    }

    // Summarises a report.
    //
    // name: the report's file name, as the summary names it
    // report: the report's lines; lines that aren't whole calls are skipped
    // Returns the summary, one target to a line, in order of kind and then target
    static String of(final String name, final String report) {
        final Map<String, Target> targets = new TreeMap<>();
        for (final String line : report.split("\n")) {
            add(targets, JsonFields.of(line));
        }
        final StringBuilder out = new StringBuilder("{\"report\":").append(Json.quote(name)).append(",\"targets\":[");
        String separator = "\n";
        for (final Target target : targets.values()) {
            out.append(separator).append(target.toJson());
            separator = ",\n";
        }
        return out.append("\n]}\n").toString();
    }

    // Counts one call under its kind and target; a line that isn't a whole call is skipped.
    private static void add(final Map<String, Target> targets, final Map<String, String> fields) {
        final String kind = fields.get("kind");
        final String target = grouped(kind, fields.get("target"));
        if (kind != null && target != null && fields.containsKey("outcome")) {
            targets.computeIfAbsent(kind + '\u0000' + target, key -> new Target(kind, target)).add(fields);
        }
    }

    // The target a call is summarised under. Libraries such as sqlite-jdbc and Netty unpack their native code to a new
    // file each run, named with a random id; in the summary each such id in a native library's file name becomes
    // *, so one library is one target, and the name is the native: entry that would allow it. Programs
    // such as embedded-redis's are unpacked to a new, randomly named folder each run, so each random id anywhere in a
    // program's path becomes *, which a process: entry can use too. The report itself keeps each full
    // path.
    //
    // kind: the call's kind
    // target: the call's target, as the report gives it
    // Returns the target, with a native library's or program's random ids as *
    static String grouped(final String kind, final String target) {
        if (target != null && Kind.PROCESS.getReportName().equals(kind)) {
            return RANDOM_ID.matcher(target).replaceAll("*");
        }
        if (target == null || !Kind.NATIVE_LIBRARY.getReportName().equals(kind)) {
            return target;
        }
        final int name = Math.max(target.lastIndexOf('/'), target.lastIndexOf('\\')) + 1;
        return target.substring(0, name) + RANDOM_ID.matcher(target.substring(name)).replaceAll("*");
    }

    // One distinct target, and what reached it.
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
