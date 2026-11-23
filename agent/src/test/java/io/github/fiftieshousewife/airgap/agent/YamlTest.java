package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

class YamlTest {

    private static final ObjectMapper YAML = new YAMLMapper();

    private static Map<String, String> entry(final String target, final String reason) {
        final Map<String, String> entry = new LinkedHashMap<>();
        entry.put("target", target);
        entry.put("reason", reason);
        return entry;
    }

    @Test
    void readsASettingsFileAsAYamlLibraryDoes() {
        final String text = "---\n# The project's stand-ins\nmode: report   # for now\n\nallow:\n"
                + "  - target: localhost:8089\n    reason: \"WireMock: the postcode service\"\n"
                + "  -   target: 'db.example.invalid:5432'\n      reason: 'it''s Testcontainers' # a comment\n"
                + "production_profiles: [prod, \"live\" , 'eu-prod']\nfile_writes:\r\n  - process:git\n"
                + "  - \"tab\\there \\u00e9 \\\"q\\\" \\\\ \\/ \\n \\r\"\n";

        final Map<String, Object> read = Yaml.read(text);

        assertAll(
                () -> assertThat(read).containsExactly(
                        Map.entry("mode", "report"),
                        Map.entry("allow", Arrays.asList(entry("localhost:8089", "WireMock: the postcode service"),
                                entry("db.example.invalid:5432", "it's Testcontainers"))),
                        Map.entry("production_profiles", Arrays.asList("prod", "live", "eu-prod")),
                        Map.entry("file_writes", Arrays.asList("process:git", "tab\there \u00e9 \"q\" \\ / \n \r"))),
                () -> assertThat(read).isEqualTo(YAML.readValue(text, Map.class)));
    }

    @Test
    void readsEmptyValuesAsNothing() {
        final Map<String, Object> read = Yaml.read("mode:\nallow: []\nfile_writes: ~\nproduction_profiles: null\n"
                + "list:\n- a\n- b: \n");

        assertAll(
                () -> assertThat(read).containsEntry("mode", null).containsEntry("allow", List.of())
                        .containsEntry("file_writes", null).containsEntry("production_profiles", null),
                () -> assertThat(read.get("list")).asInstanceOf(InstanceOfAssertFactories.LIST)
                        .containsExactly("a", Collections.singletonMap("b", null)),
                () -> assertThat(Yaml.read("")).isEmpty(),
                () -> assertThat(Yaml.read("# only a comment\n\n")).isEmpty());
    }

    @Test
    void namesTheLineOfAnythingItDoesntRead() {
        assertAll(
                () -> refuses("  mode: report\n", "line 1 is indented, but nothing above it"),
                () -> refuses("mode report\n", "line 1 needs the form 'key: value'"),
                () -> refuses("mode: report\nmode: off\n", "line 2 repeats the key 'mode'"),
                () -> refuses("allow: x\n  - a\n", "line 1 has a value on its own line and a list below it"),
                () -> refuses("allow:\n  - a\n   - b\n", "line 3 isn't lined up"),
                () -> refuses("allow:\n  - a\n  b\n", "line 3 isn't lined up"),
                () -> refuses("allow:\n  -\n", "line 2 is an empty '- ' item"),
                () -> refuses("allow:\n  - target: a\n     reason: b\n", "line 3 isn't lined up with the key above"),
                () -> refuses("allow:\n  - target: a\n    reason: b\n    reason: c\n", "line 4 repeats the key"),
                () -> refuses("allow:\n  - target: a\n    - b\n", "line 3 needs the form 'key: value'"),
                () -> refuses("allow:\n\t- a\n", "line 2 is indented with a tab"));
    }

    @Test
    void refusesTextYamlWouldReadAsSomethingElse() {
        assertAll(
                () -> refuses("mode: &anchor report\n", "line 1: '&anchor report' starts with a character"),
                () -> refuses("mode: |\n", "starts with a character YAML gives a meaning to"),
                () -> refuses("mode: {a: b}\n", "starts with a character"),
                () -> refuses("reason: WireMock: postcodes\n", "line 1: 'WireMock: postcodes' holds a colon"),
                () -> refuses("reason: WireMock:\n", "holds a colon"),
                () -> refuses("reason: \"open\n", "ends before its closing quote"),
                () -> refuses("reason: 'open\n", "ends before its closing quote"),
                () -> refuses("reason: \"a\" b\n", "'b' follows the value"),
                () -> refuses("reason: \"\\x\"\n", "'\\x' isn't an escape"),
                () -> refuses("reason: \"\\u00\"\n", "four hexadecimal digits"),
                () -> refuses("reason: \"\\u00zz\"\n", "'\\u00zz' needs four hexadecimal digits"),
                () -> refuses("list: [a b\n", "ends before its closing"),
                () -> refuses("list: [a,\n", "ends before"),
                () -> refuses("list: [\"a\" b]\n", "needs a comma"),
                () -> refuses("list: [a] b\n", "'b' follows the value"),
                () -> refuses("list: [[a]]\n", "starts with a character"));
    }

    @Test
    void readsAHashInsideTextAsText() {
        final String text = "reason: issue#12 \"quoted\" # but this is a comment\nlist: [a#1]\nnone: #a comment\n"
                + "spaced:   [ a ,  'b' ]\n";

        assertThat(Yaml.read(text)).isEqualTo(YAML.readValue(text, Map.class))
                .containsEntry("reason", "issue#12 \"quoted\"").containsEntry("list", List.of("a#1"))
                .containsEntry("none", null).containsEntry("spaced", List.of("a", "b"));
    }

    private static void refuses(final String text, final String message) {
        assertThatIllegalArgumentException().isThrownBy(() -> Yaml.read(text)).withMessageContaining(message);
    }
}
