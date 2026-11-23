package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

class SettingsFileTest {

    private static final YAMLMapper YAML = new YAMLMapper();

    private static final String EXAMPLE = "# yaml-language-server: $schema=../schema/airgap-settings.schema.json\n"
            + "mode: report\nallow:\n  - target: localhost:8089\n    reason: WireMock standing in for the postcode"
            + " service\n  - target: process:git\n    reason: \"The release notes read the history\"\n"
            + "production_profiles: [prod]\nfile_writes: refuse\n";

    @Test
    void readsTheModeAndTheTargetsItAllows() {
        final SettingsFile file = SettingsFile.read(EXAMPLE);

        assertAll(
                () -> assertThat(file.getMode()).isEqualTo(Mode.REPORT),
                () -> assertThat(file.getAllow()).containsExactly("localhost:8089", "process:git"),
                () -> assertThat(SettingsFile.read("mode: off\n").getMode()).isEqualTo(Mode.OFF),
                () -> assertThat(SettingsFile.read("allow: []\n").getMode()).as("not set").isNull(),
                () -> assertThat(SettingsFile.read("").getAllow()).isEmpty());
    }

    static Stream<Arguments> files() {
        return Stream.of(
                Arguments.of(EXAMPLE, null),
                Arguments.of("", null),
                Arguments.of("allow: []\nproduction_profiles: []\n", null),
                Arguments.of("mode: refuse\n", null),
                Arguments.of("mode: sometimes\n", "mode is 'sometimes'; it must be one of [refuse, report, off]"),
                Arguments.of("mode: Report\n", "mode is 'Report'"),
                Arguments.of("mode: [refuse]\n", "mode is [refuse]"),
                Arguments.of("allwo: []\n", "unknown setting 'allwo'; known settings: [mode, allow,"),
                Arguments.of("allow:\n",
                        "allow must be a list of stand-ins, each with a target and a reason, not empty"),
                Arguments.of("allow: localhost:8089\n", "allow must be a list of stand-ins"),
                Arguments.of("allow:\n  - localhost:8089\n", "allow entry 1 ('localhost:8089') needs a target and a"
                        + " reason, for example '- target: localhost:8089'"),
                Arguments.of("allow:\n  - target: a.example.invalid\n    reason: a\n  - target: localhost:8089\n",
                        "allow entry 2 (localhost:8089) needs a reason: what the stand-in is"),
                Arguments.of("allow:\n  - target: localhost:8089\n    reason: \"\"\n", "needs a reason"),
                Arguments.of("allow:\n  - reason: WireMock\n", "allow entry 1's target must be text, not empty"),
                Arguments.of("allow:\n  - target: \"\"\n    reason: a\n", "allow entry 1's target must be text"),
                Arguments.of("allow:\n  - target: \"local host\"\n    reason: a\n",
                        "allow entry 1's target 'local host' has a space in it"),
                Arguments.of("allow:\n  - target: a\n    reason: b\n    port: 1\n",
                        "allow entry 1 has 'port'; an entry has only [target, reason]"),
                Arguments.of("production_profiles: prod\n", "production_profiles must be a list of Spring profile"),
                Arguments.of("production_profiles:\n  - name: prod\n", "production_profiles entry must be text"),
                Arguments.of("production_profiles: [\"\"]\n", "production_profiles entry must be text, not ''"),
                Arguments.of("file_writes: never\n", "file_writes is 'never'; it must be one of [report, refuse]"));
    }

    /** The agent can't read the schema itself, so its checks are held to agree with it, file by file. */
    @ParameterizedTest
    @MethodSource("files")
    void acceptsExactlyWhatTheSchemaAccepts(final String text, final String problem) throws IOException {
        final JsonNode read = text.isEmpty() ? JsonNodeFactory.instance.objectNode() : YAML.readTree(text);
        final ReportSchema schema = new ReportSchema("airgap.settingsSchema");

        if (problem == null) {
            assertAll(
                    () -> assertThat(schema.problems(read)).isEmpty(),
                    () -> assertThatCode(() -> SettingsFile.read(text)).doesNotThrowAnyException());
        } else {
            assertAll(
                    () -> assertThat(schema.problems(read)).isNotEmpty(),
                    () -> assertThatIllegalArgumentException().isThrownBy(() -> SettingsFile.read(text))
                            .withMessageContaining(problem));
        }
    }
}
