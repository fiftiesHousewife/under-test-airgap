package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// With the agent attached, a Spring Boot application's refused calls name the property that held the address, where
// it was read, and the active profiles; a call made while Spring starts is marked; a production profile stops it.
class SpringTest {

    private static final String AT_START_UP = "spring-start-up.example.invalid";

    @Configuration(proxyBeanMethods = false)
    static class Application {
        @Bean
        InitializingBean looksSomethingUpAsItStarts() {
            return () -> {
                try {
                    InetAddress.getByName(AT_START_UP);
                } catch (UnknownHostException refused) {
                    // refused by the agent: the application starts anyway
                }
            };
        }
    }

    private static ConfigurableApplicationContext start(final String profile) {
        return new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF).properties("spring.config.name=airgap-spring").profiles(profile).run();
    }

    @Test
    void namesTheSettingWhereItWasReadAndTheProfiles() throws IOException {
        try (ConfigurableApplicationContext context = start("stand-in")) {
            final String host = context.getEnvironment().getProperty("orders.host");
            assertAll(
                    () -> assertThatThrownBy(() -> InetAddress.getByName(host))
                            .isInstanceOf(UnknownHostException.class),
                    () -> assertThat(ReportLines.aboutExactly("orders.example.invalid")).anyMatch(line ->
                            line.contains("\"setting\":{\"name\":\"orders.host\",\"from\":\"class path resource "
                                    + "[airgap-spring.properties] - 1:")
                                    && line.contains("\"profiles\":[\"stand-in\"]")));
        }
    }

    @Test
    void marksACallMadeWhileSpringStarts() throws IOException {
        try (ConfigurableApplicationContext context = start("stand-in")) {
            assertThat(ReportLines.aboutExactly(AT_START_UP)).as(context.getId())
                    .anyMatch(line -> line.contains("\"spring_start_up\":true"));
        }
    }

    @Test
    void failsToStartUnderAProductionProfile() {
        assertThatThrownBy(() -> start("prod").close()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production profile 'prod'");
    }
}
