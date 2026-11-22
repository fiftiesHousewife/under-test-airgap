package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void hasSafeDefaults() {
        final Settings settings = Settings.parse(null);

        assertAll(
                () -> assertThat(settings.getMode()).isEqualTo(Mode.REFUSE),
                () -> assertThat(settings.getReport()).isEqualTo(Path.of("build", "airgap", "calls.jsonl")),
                () -> assertThat(settings.getAllowlist().getEntries()).isEmpty(),
                () -> assertThat(settings.getRun()).isEqualTo("test"),
                () -> assertThat(settings.getProject()).isEmpty(),
                () -> assertThat(settings.getWithoutHooks()).isEmpty(),
                () -> assertThat(Settings.parse("  ").getMode()).isEqualTo(Mode.REFUSE));
    }

    @Test
    void readsEverySetting() {
        final Settings settings = Settings.parse(" mode=Report ; report=out/calls.jsonl;allow=a.example:1, process:git;"
                + "run=application;project=com.example.;withoutHooks=tcp-socket;;");

        assertAll(
                () -> assertThat(settings.getMode()).isEqualTo(Mode.REPORT),
                () -> assertThat(settings.getReport()).isEqualTo(Path.of("out/calls.jsonl")),
                () -> assertThat(settings.getAllowlist().getEntries()).containsExactly("a.example:1", "process:git"),
                () -> assertThat(settings.getRun()).isEqualTo("application"),
                () -> assertThat(settings.getProject()).containsExactly("com.example."),
                () -> assertThat(settings.getWithoutHooks()).containsExactly("tcp-socket"));
    }

    @Test
    void refusesMistakesRatherThanIgnoringThem() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("allwo=a.example"))
                        .withMessageContaining("unknown setting 'allwo'"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("refuse"))
                        .withMessageContaining("key=value"),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> Settings.parse("mode=sometimes")));
    }
}
