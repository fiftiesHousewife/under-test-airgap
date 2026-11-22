package io.github.fiftieshousewife.airgap.tools.repochecks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoChecksTest {

    @TempDir
    Path root;

    private void writeRepo() throws IOException {
        for (final String folder : RepoChecks.DECISION_FOLDERS) {
            final Path dir = Files.createDirectories(root.resolve(folder));
            Files.writeString(dir.resolve("README.md"), "# Index\n\n" + DecisionIndex.MARKER + "\n");
            Files.writeString(dir.resolve("0001-only.md"), DecisionIndexTest.record("Only decision", "Group"));
        }
        Files.writeString(root.resolve("README.md"), "[broken](nowhere.md) " + GUIDE_LINK);
    }

    private static final String GUIDE_LINK = "[only](decision-guides/0001-only.md)";

    @Test
    void checkReportsBrokenLinksAndStaleIndexesUntilIndexesAreRebuilt() throws IOException {
        writeRepo();
        final int before = RepoChecks.check(root).size();
        RepoChecks.main("index", root.toString());

        assertAll(
                () -> assertThat(before).isEqualTo(3),
                () -> assertThat(RepoChecks.check(root)).singleElement()
                        .extracting(Problem::message).isEqualTo("broken link to nowhere.md"));
    }

    @Test
    void mainFailsOnProblemsAndPassesWhenClean() throws IOException {
        writeRepo();
        RepoChecks.main("index", root.toString());

        assertThatIllegalStateException().isThrownBy(() -> RepoChecks.main("check", root.toString()));
        Files.writeString(root.resolve("README.md"), GUIDE_LINK);
        RepoChecks.main("check", root.toString());
    }

    @Test
    void skipsDecisionFoldersTheRepoDoesNotHave() throws IOException {
        final Path decisions = Files.createDirectories(root.resolve("docs/decisions"));
        Files.writeString(decisions.resolve("README.md"), "# Index\n\n" + DecisionIndex.MARKER + "\n");
        Files.writeString(root.resolve("README.md"), "No guides here.");
        RepoChecks.main("index", root.toString());

        assertThat(RepoChecks.check(root)).isEmpty();
    }

    @Test
    void mainFixesLinks() throws IOException {
        writeRepo();
        RepoChecks.main("fix-links", root.toString());

        assertThat(Files.readString(root.resolve("README.md"))).isEqualTo("[broken](nowhere.md) " + GUIDE_LINK);
    }

    @Test
    void mainRejectsBadArguments() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(RepoChecks::main),
                () -> assertThatIllegalArgumentException()
                        .isThrownBy(() -> RepoChecks.main("unknown", root.toString())));
    }
}
