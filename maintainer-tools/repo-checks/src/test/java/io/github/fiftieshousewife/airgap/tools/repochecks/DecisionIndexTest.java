package io.github.fiftieshousewife.airgap.tools.repochecks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecisionIndexTest {

    @TempDir
    Path folder;

    static String record(final String title, final String group) {
        return "---\nstatus: proposed\ndecision-makers: Test Quality Lead\ngroup: " + group + "\n---\n\n# " + title
                + "\n\n**Decided by:** Test Quality Lead · **Status:** proposed (open)\n";
    }

    private void writeFolder() throws IOException {
        final String readme = "# Decisions\n\nIntro written by people.\n\n" + DecisionIndex.MARKER + "\nstale\n";
        Files.writeString(folder.resolve("README.md"), readme);
        Files.writeString(folder.resolve("0001-first.md"), record("First decision", "Safety"));
        Files.writeString(folder.resolve("0002-second.md"), record("Second decision", "Hygiene"));
        Files.writeString(folder.resolve("0003-third.md"), record("Third decision", "Safety"));
    }

    @Test
    void keepsTheIntroductionAndGroupsRecordsInNumberingOrder() throws IOException {
        writeFolder();

        final String index = new DecisionIndex().render(folder);

        assertAll(
                () -> assertThat(index)
                        .startsWith("# Decisions\n\nIntro written by people.\n\n" + DecisionIndex.MARKER),
                () -> assertThat(index.indexOf("## Safety")).isLessThan(index.indexOf("## Hygiene")),
                () -> assertThat(index)
                        .contains("| [Third decision](0003-third.md) | proposed (open) | Test Quality Lead |"),
                () -> assertThat(index).doesNotContain("stale"));
    }

    @Test
    void isCurrentOnlyAfterWriting() throws IOException {
        writeFolder();
        final DecisionIndex decisionIndex = new DecisionIndex();
        final boolean before = decisionIndex.isCurrent(folder);
        decisionIndex.write(folder);

        assertAll(
                () -> assertThat(before).isFalse(),
                () -> assertThat(decisionIndex.isCurrent(folder)).isTrue());
    }

    @Test
    void refusesAReadmeWithoutTheMarker() throws IOException {
        Files.writeString(folder.resolve("README.md"), "# No marker\n");

        assertThatIllegalStateException().isThrownBy(() -> new DecisionIndex().render(folder));
    }

    @Test
    void refusesARecordWithoutATitle() throws IOException {
        final Path file = folder.resolve("0001-untitled.md");
        Files.writeString(file, "---\nstatus: proposed\ndecision-makers: x\ngroup: y\n---\n");

        assertThatIllegalStateException().isThrownBy(() -> DecisionRecord.read(file));
    }
}
