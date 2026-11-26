package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.settings.SettingsFile;
import org.junit.jupiter.api.Test;

// The Spring profiles that mean production: prod, production and live unless the settings file names its own, and
// none at all if it names an empty list.
class ProductionProfilesTest {

    @Test
    void areProdProductionAndLiveUnlessTheFileSaysOtherwise() {
        assertAll(
                () -> assertThat(Settings.parse(null).getProductionProfiles())
                        .containsExactly("prod", "production", "live"),
                () -> assertThat(SettingsFile.read("mode: report\n").getProductionProfiles())
                        .isEqualTo(SettingsFile.DEFAULT_PRODUCTION_PROFILES),
                () -> assertThat(SettingsFile.read("production_profiles: [prd, release]\n").getProductionProfiles())
                        .containsExactly("prd", "release"),
                () -> assertThat(SettingsFile.read("production_profiles: []\n").getProductionProfiles())
                        .as("an empty list turns the stop off").isEmpty());
    }
}
