package io.github.fiftieshousewife.airgap.tools.repochecks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Entry point. {@code check <root>} fails on broken links, YAML files that don't match their schema, and out-of-date
 * decision indexes. {@code index <root>} rebuilds the decision indexes. {@code fix-links <root>} re-points links to
 * decision records at their current files, after renumbering or moving them.
 */
@Slf4j
public final class RepoChecks {

    static final List<String> DECISION_FOLDERS = List.of("docs/decisions", "decision-guides");

    private RepoChecks() {
    }

    private static final Map<String, Consumer<Path>> COMMANDS = Map.of(
            "check", root -> failOn(check(root)),
            "index", root -> decisionFolders(root).forEach(folder -> new DecisionIndex().write(folder)),
            "fix-links", root -> log.info("{} file(s) relinked", new DecisionLinkFixer().fix(new RepoFiles(root))));

    public static void main(final String... args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: RepoChecks check|index|fix-links <repository root>");
        }
        final Consumer<Path> command = COMMANDS.get(args[0]);
        if (command == null) {
            throw new IllegalArgumentException("unknown command: " + args[0]);
        }
        command.accept(Path.of(args[1]));
    }

    static List<Problem> check(final Path root) {
        final RepoFiles files = new RepoFiles(root);
        final List<Problem> problems = new ArrayList<>(new LinkChecker().check(files));
        problems.addAll(new YamlSchemaChecker().check(files));
        problems.addAll(new GuideCoverage().check(root));
        for (final Path folder : decisionFolders(root)) {
            if (!new DecisionIndex().isCurrent(folder)) {
                problems.add(new Problem(folder.resolve("README.md"),
                        "index is out of date; run ./gradlew buildDecisionIndexes"));
            }
        }
        return problems;
    }

    // The decision folders this repo has: not every repo that reuses these checks has decision guides.
    static List<Path> decisionFolders(final Path root) {
        return DECISION_FOLDERS.stream().map(root::resolve).filter(Files::isDirectory).toList();
    }

    private static void failOn(final List<Problem> problems) {
        problems.forEach(problem -> log.error("{}", problem.toString().replace('\r', ' ').replace('\n', ' ')));
        if (!problems.isEmpty()) {
            throw new IllegalStateException(problems.size() + " problem(s) in the repository's own files");
        }
        log.info("Repository files are in order: links, YAML schemas, decision indexes, and every decision guide"
                + " linked from the README.");
    }
}
