package io.github.fiftieshousewife.undertest.tools.repochecks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuideCoverageTest {

    @TempDir
    Path root;

    @Test
    void reportsEachGuideTheReadmeDoesNotLink() throws IOException {
        final Path guides = Files.createDirectories(root.resolve("decision-guides"));
        Files.writeString(guides.resolve("0001-linked.md"), "# Linked\n");
        Files.writeString(guides.resolve("0002-new.md"), "# New\n");
        Files.writeString(guides.resolve("README.md"), "# Index\n");
        Files.writeString(root.resolve("README.md"), "[Linked](decision-guides/0001-linked.md)\n");

        assertThat(new GuideCoverage().check(root)).singleElement().extracting(Problem::message)
                .isEqualTo("doesn't link the decision guide 0002-new.md; add it to its step of the journey");
    }

    @Test
    void passesWithNoGuidesFolder() {
        assertThat(new GuideCoverage().check(root)).isEmpty();
    }

    @Test
    void reportsEveryGuideWhenThereIsNoReadme() throws IOException {
        final Path guides = Files.createDirectories(root.resolve("decision-guides"));
        Files.writeString(guides.resolve("0001-only.md"), "# Only\n");

        assertThat(new GuideCoverage().check(root)).hasSize(1);
    }
}
