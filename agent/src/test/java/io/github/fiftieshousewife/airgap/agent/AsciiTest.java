package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class AsciiTest {

    @Test
    void foldsOnlyTheLettersAToZ() {
        assertAll(
                () -> assertThat(Ascii.equalsIgnoringCase("LocalHost", "localhost")).isTrue(),
                () -> assertThat(Ascii.equalsIgnoringCase("REPORT", "report")).isTrue(),
                () -> assertThat(Ascii.equalsIgnoringCase("localhost", "localhosts")).isFalse(),
                () -> assertThat(Ascii.equalsIgnoringCase("İ", "i")).as("no Unicode folding").isFalse(),
                () -> assertThat(Ascii.equalsIgnoringCase("a-1", "b-1")).isFalse());
    }

    @Test
    void comparesEndings() {
        assertAll(
                () -> assertThat(Ascii.endsWithIgnoringCase("App.LOCALHOST", ".localhost")).isTrue(),
                () -> assertThat(Ascii.endsWithIgnoringCase("host", ".localhost")).isFalse(),
                () -> assertThat(Ascii.endsWithIgnoringCase("app.example", ".localhost")).isFalse());
    }

    @Test
    void lowersOnlyTheLettersAToZ() {
        assertAll(
                () -> assertThat(Ascii.lowerCase("LDAP://Host-1")).isEqualTo("ldap://host-1"),
                () -> assertThat(Ascii.lowerCase("İSTANBUL")).as("no Unicode folding").isEqualTo("İstanbul"),
                () -> assertThat(Ascii.lowerCase("")).isEmpty());
    }
}
