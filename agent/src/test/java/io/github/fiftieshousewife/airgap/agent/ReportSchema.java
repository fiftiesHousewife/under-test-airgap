package io.github.fiftieshousewife.airgap.agent;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One of the agent's schemas, for checking what it writes and reads: the report's,
 * {@code schema/airgap-report.schema.json}, the summary's, {@code schema/airgap-summary.schema.json}, or the settings
 * file's, {@code schema/airgap-settings.schema.json}, each named by a system property the build sets.
 */
final class ReportSchema {

    private static final JsonMapper JSON = new JsonMapper();

    private final Schema schema;

    /** The report's schema, from the {@code airgap.schema} property. */
    ReportSchema() throws IOException {
        this("airgap.schema");
    }

    /** @param property the system property that names the schema's file */
    ReportSchema(final String property) throws IOException {
        final String text = Files.readString(Path.of(System.getProperty(property)));
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(text);
    }

    /** @return what's wrong with one line of the report, or with a summary; empty if it matches the schema */
    List<Error> problems(final String line) {
        return problems(JSON.readTree(line));
    }

    /** @return what's wrong with a document already read, such as a settings file; empty if it matches the schema */
    List<Error> problems(final JsonNode document) {
        return schema.validate(document);
    }
}
