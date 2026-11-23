package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsonFieldsTest {

    @Test
    void readsTheTextFieldsOfALineTheAgentWrote() {
        final String line = Call.builder().kind(Kind.TCP).target("db.example.invalid:5432").outcome("refused")
                .run("test").javaVersion("25").test("OrdersTest.saves")
                .caller("com.example.Orders.save(Orders.java:12)").stack(List.of("a {", "b ]")).build().toJson();

        assertThat(JsonFields.of(line)).containsOnlyKeys("kind", "target", "outcome", "test", "caller")
                .containsEntry("kind", "tcp").containsEntry("target", "db.example.invalid:5432")
                .containsEntry("outcome", "refused").containsEntry("test", "OrdersTest.saves")
                .containsEntry("caller", "com.example.Orders.save(Orders.java:12)");
    }

    @Test
    void undoesEveryEscape() {
        assertThat(JsonFields.of("{\"target\":\"q\\\" s\\\\ n\\n r\\r t\\t b\\b f\\f /\\/ e\\u00e9\"}"))
                .containsEntry("target", "q\" s\\ n\n r\r t\t b\b f\f // e\u00e9");
    }

    @Test
    void skipsNestedObjectsAndArraysEvenWithBracketsInTheirText() {
        assertThat(JsonFields.of(" { \"run\" : {\"kind\":\"te}st\",\"deep\":[[\"]\"],{}]} , \"stack\":[\"[\"],"
                + "\"kind\" : \"udp\" } "))
                .containsOnlyKeys("kind").containsEntry("kind", "udp");
    }

    @Test
    void readsOtherValuesAsTheirText() {
        assertThat(JsonFields.of("{\"calls\":12,\"whole\":true,\"none\":null}"))
                .containsEntry("calls", "12").containsEntry("whole", "true").containsEntry("none", "null");
    }

    @Test
    void readsNothingFromALineThatIsntOneWholeObject() {
        assertAll(
                () -> assertThat(JsonFields.of("")).as("blank").isEmpty(),
                () -> assertThat(JsonFields.of("{}")).as("empty").isEmpty(),
                () -> assertThat(JsonFields.of("{\"kind\":\"tcp\",\"target\":\"db.exa")).as("cut short").isEmpty(),
                () -> assertThat(JsonFields.of("{\"kind\":\"tcp\"} x")).as("more after it").isEmpty(),
                () -> assertThat(JsonFields.of("[\"kind\"]")).as("not an object").isEmpty(),
                () -> assertThat(JsonFields.of("{\"kind\" \"tcp\"}")).as("no colon").isEmpty(),
                () -> assertThat(JsonFields.of("{\"a\":\"1\" \"b\":\"2\"}")).as("no comma").isEmpty(),
                () -> assertThat(JsonFields.of("{\"run\":{\"kind\":\"test\"")).as("nested, cut short").isEmpty(),
                () -> assertThat(JsonFields.of("{\"target\":\"\\uzzzz\"}")).as("a broken escape").isEmpty());
    }
}
