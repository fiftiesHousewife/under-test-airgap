package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class JUnit4NamesTest {

    @Test
    void namesATestAsClassDotMethod() {
        assertThat(JUnit4Names.testName("placesAnOrder(com.example.OrderServiceTest)"))
                .isEqualTo("com.example.OrderServiceTest.placesAnOrder");
    }

    @Test
    void keepsAParameterizedTestsParameters() {
        assertAll(
                () -> assertThat(JUnit4Names.testName("connects[external](com.example.ConnectTest)"))
                        .isEqualTo("com.example.ConnectTest.connects[external]"),
                () -> assertThat(JUnit4Names.testName("connects[a (b)](com.example.ConnectTest)"))
                        .as("brackets in a parameter's name").isEqualTo("com.example.ConnectTest.connects[a (b)]"));
    }

    @Test
    void namesADescriptionThatIsntAMethodAsItReads() {
        assertThat(JUnit4Names.testName("com.example.AllTests")).isEqualTo("com.example.AllTests");
    }
}
