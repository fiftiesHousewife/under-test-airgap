package io.github.fiftieshousewife.airgap.agent;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** The report's schema, {@code schema/airgap-report.schema.json}, for checking lines the agent writes. */
final class ReportSchema {

    private static final JsonMapper JSON = new JsonMapper();

    private final Schema schema;

    ReportSchema() throws IOException {
        final String text = Files.readString(Path.of(System.getProperty("airgap.schema")));
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(text);
    }

    /** @return what's wrong with one line of the report; empty if it matches the schema */
    List<Error> problems(final String line) {
        return schema.validate(JSON.readTree(line));
    }
}
