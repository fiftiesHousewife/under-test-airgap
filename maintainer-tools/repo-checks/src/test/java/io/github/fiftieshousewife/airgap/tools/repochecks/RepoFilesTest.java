package io.github.fiftieshousewife.airgap.tools.repochecks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoFilesTest {

    @TempDir
    Path root;

    @Test
    void skipsBuildOutputWorkingNotesAndInstalledSkills() throws IOException {
        Files.createDirectories(root.resolve("build"));
        Files.createDirectories(root.resolve("research_notes"));
        Files.createDirectories(root.resolve(".claude/skills/grilling"));
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("build/generated.md"), "x");
        Files.writeString(root.resolve("research_notes/notes.md"), "x");
        Files.writeString(root.resolve(".claude/skills/grilling/SKILL.md"), "[a broken link](missing.md)");
        Files.writeString(root.resolve("docs/kept.md"), "x");
        Files.writeString(root.resolve("docs/kept.yaml"), "x");

        assertThat(new RepoFiles(root).withExtension(".md")).containsExactly(root.resolve("docs/kept.md"));
    }
}
