package io.github.fiftieshousewife.airgap.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * {@link AllowCalls} on a class and on a method allows those stand-ins for that test only. Checked with the agent's
 * own answer to "would this be allowed?", so no call is made.
 */
@AllowCalls("partner.example.invalid:443")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AllowCallsTest {

    @Test
    @Order(1)
    @AllowCalls({"wiremock.example.invalid:8089", "process:git"})
    void allowsWhatTheClassAndTheMethodName() {
        assertAll(
                () -> assertThat(Gate.isAllowed("partner.example.invalid", 443)).isTrue(),
                () -> assertThat(Gate.isAllowed("wiremock.example.invalid", 8089)).isTrue(),
                () -> assertThat(Gate.isAllowed("db.example.invalid", 5432)).isFalse());
    }

    @Test
    @Order(2)
    void allowancesEndWithTheirTest() {
        assertAll(
                () -> assertThat(Gate.isAllowed("partner.example.invalid", 443)).isTrue(),
                () -> assertThat(Gate.isAllowed("wiremock.example.invalid", 8089)).isFalse());
    }
}
