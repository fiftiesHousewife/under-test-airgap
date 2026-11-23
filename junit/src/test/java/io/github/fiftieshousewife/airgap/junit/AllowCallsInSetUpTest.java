package io.github.fiftieshousewife.airgap.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Gate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A class's {@link AllowCalls} covers its set-up and tear-down too, and is inherited from base classes and the classes
 * a test is nested in. Checked with the agent's own answer to "would this be allowed?", so no call is made.
 */
@AllowCalls("setup.example.invalid:443")
class AllowCallsInSetUpTest extends AllowCallsBase {

    private static boolean allowedBeforeAll;
    private static String namedBeforeAll;
    private final boolean allowedInConstructor;

    AllowCallsInSetUpTest() {
        allowedInConstructor = Gate.isAllowed("setup.example.invalid", 443);
    }

    @BeforeAll
    static void lookAround() {
        allowedBeforeAll = Gate.isAllowed("setup.example.invalid", 443);
        namedBeforeAll = Gate.currentTest();
    }

    @AfterAll
    static void lookAroundAgain() {
        assertAll(
                () -> assertThat(Gate.isAllowed("setup.example.invalid", 443)).isTrue(),
                () -> assertThat(Gate.isAllowed("method.example.invalid", 443)).as("the method's ended").isFalse(),
                () -> assertThat(Gate.currentTest()).isEqualTo(AllowCallsInSetUpTest.class.getName()));
    }

    @Test
    void coversBeforeAllAndTheConstructor() {
        assertAll(
                () -> assertThat(allowedBeforeAll).isTrue(),
                () -> assertThat(namedBeforeAll).isEqualTo(AllowCallsInSetUpTest.class.getName()),
                () -> assertThat(allowedInConstructor).isTrue());
    }

    @Test
    @AllowCalls("method.example.invalid:443")
    void addsTheMethodsToTheClassesAndTheBaseClasses() {
        assertAll(
                () -> assertThat(Gate.isAllowed("setup.example.invalid", 443)).isTrue(),
                () -> assertThat(Gate.isAllowed("base.example.invalid", 443)).isTrue(),
                () -> assertThat(Gate.isAllowed("method.example.invalid", 443)).isTrue(),
                () -> assertThat(Gate.isAllowed("db.example.invalid", 5432)).isFalse());
    }

    @Nested
    @AllowCalls("nested.example.invalid:443")
    class WhenNested {

        @Test
        void inheritsTheEnclosingClasses() {
            assertAll(
                    () -> assertThat(Gate.isAllowed("nested.example.invalid", 443)).isTrue(),
                    () -> assertThat(Gate.isAllowed("setup.example.invalid", 443)).isTrue(),
                    () -> assertThat(Gate.isAllowed("base.example.invalid", 443)).isTrue(),
                    () -> assertThat(Gate.currentTest()).endsWith("WhenNested.inheritsTheEnclosingClasses"));
        }
    }
}

/** A base class whose {@link AllowCalls} its subclasses' tests inherit. */
@AllowCalls("base.example.invalid:443")
abstract class AllowCallsBase {
}
