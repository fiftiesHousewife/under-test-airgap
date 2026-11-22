package io.github.fiftieshousewife.airgap.tools.repochecks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecisionLinkFixerTest {

    @TempDir
    Path root;

    @Test
    void repointsRenumberedAndMovedRecordsInMarkdownAndYaml() throws IOException {
        Files.createDirectories(root.resolve("docs/decisions"));
        Files.createDirectories(root.resolve("decision-guides"));
        Files.writeString(root.resolve("docs/decisions/0002-how-it-is-organised.md"), "# x");
        Files.writeString(root.resolve("decision-guides/0005-what-tests-mean.md"), "# y");
        Files.writeString(root.resolve("README.md"),
                "[a](docs/decisions/0001-how-it-is-organised.md) [b](docs/decisions/0009-what-tests-mean.md)"
                        + " [c](https://example.com/0001-how-it-is-organised.md) [d](0003-unknown-record.md)");
        Files.writeString(root.resolve("plan.yaml"), "decision_record: \"docs/decisions/0009-what-tests-mean.md\"\n");

        final int changed = new DecisionLinkFixer().fix(new RepoFiles(root));

        assertAll(
                () -> assertThat(changed).isEqualTo(2),
                () -> assertThat(Files.readString(root.resolve("README.md")))
                        .contains("[a](docs/decisions/0002-how-it-is-organised.md)")
                        .contains("[b](decision-guides/0005-what-tests-mean.md)")
                        .contains("[d](0003-unknown-record.md)"),
                () -> assertThat(Files.readString(root.resolve("plan.yaml")))
                        .contains("\"decision-guides/0005-what-tests-mean.md\""));
    }

    @Test
    void changesNothingWhenLinksAreAlreadyCurrent() throws IOException {
        Files.createDirectories(root.resolve("decision-guides"));
        Files.writeString(root.resolve("decision-guides/0001-current.md"), "# z");
        Files.writeString(root.resolve("README.md"), "[ok](decision-guides/0001-current.md)");

        assertThat(new DecisionLinkFixer().fix(new RepoFiles(root))).isZero();
    }
}
