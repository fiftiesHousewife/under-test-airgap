package io.github.fiftieshousewife.undertest.tools.repochecks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlSchemaCheckerTest {

    private static final String SCHEMA = """
            {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object",
             "required": ["name"], "properties": {"name": {"type": "string"}}, "additionalProperties": false}
            """;

    @TempDir
    Path root;

    @BeforeEach
    void writeSchema() throws IOException {
        Files.writeString(root.resolve("thing.schema.json"), SCHEMA);
    }

    private List<Problem> check(final String yaml) throws IOException {
        final Path file = root.resolve("thing.yaml");
        Files.writeString(file, yaml);
        return new YamlSchemaChecker().check(new RepoFiles(root));
    }

    @Test
    void acceptsAFileThatMatchesItsSchema() throws IOException {
        assertThat(check("# yaml-language-server: $schema=thing.schema.json\nname: widget\n")).isEmpty();
    }

    @Test
    void reportsAFileThatBreaksItsSchema() throws IOException {
        assertThat(check("# yaml-language-server: $schema=thing.schema.json\ncolour: red\n")).isNotEmpty();
    }

    @Test
    void reportsMissingAndUnfindableSchemas() throws IOException {
        final List<Problem> noLine = check("name: widget\n");
        final List<Problem> notFound = check("# yaml-language-server: $schema=missing.schema.json\nname: widget\n");

        assertAll(
                () -> assertThat(noLine).singleElement()
                        .extracting(Problem::message).asString().startsWith("no schema"),
                () -> assertThat(notFound).singleElement()
                        .extracting(Problem::message).asString().startsWith("schema not found"));
    }

    @Test
    void trustsSchemasOnTheWebWithoutFetchingThem() throws IOException {
        assertThat(check("# yaml-language-server: $schema=https://example.com/s.json\nanything: 1\n")).isEmpty();
    }
}
