package io.github.fiftieshousewife.undertest.tools.repochecks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkCheckerTest {

    @TempDir
    Path root;

    @Test
    void reportsOnlyLocalLinksToMissingFiles() throws IOException {
        Files.writeString(root.resolve("present.md"), "# Present");
        Files.writeString(root.resolve("page.md"), """
                [ok](present.md) [anchor](present.md#section) [web](https://example.com/x.md)
                [mail](mailto:someone@example.com) [missing](absent.md)
                """);

        final List<Problem> problems = new LinkChecker().check(new RepoFiles(root));

        assertAll(
                () -> assertThat(problems).hasSize(1),
                () -> assertThat(problems.getFirst().message()).isEqualTo("broken link to absent.md"),
                () -> assertThat(problems.getFirst().toString()).endsWith("page.md: broken link to absent.md"));
    }
}
