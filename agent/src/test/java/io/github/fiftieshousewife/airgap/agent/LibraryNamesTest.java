package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class LibraryNamesTest {

    @Test
    void namesALibraryByItsFileWithoutPrefixExtensionOrVersion() {
        assertAll(
                () -> assertThat(LibraryNames.nameOf("/usr/lib/libz.so.1.2.13")).isEqualTo("z"),
                () -> assertThat(LibraryNames.nameOf("libfoo.so.0")).isEqualTo("foo"),
                () -> assertThat(LibraryNames.nameOf("libfoo.so.9")).isEqualTo("foo"),
                () -> assertThat(LibraryNames.nameOf("libfoo.so.")).as("not a version").isEqualTo("foo.so."),
                () -> assertThat(LibraryNames.nameOf("foo.1")).as("only a .so has versions").isEqualTo("foo.1"),
                () -> assertThat(LibraryNames.nameOf("libfoo.so.1a")).isEqualTo("foo.so.1a"),
                () -> assertThat(LibraryNames.nameOf("libfoo.so./")).isEqualTo(""),
                () -> assertThat(LibraryNames.nameOf("C:\\x\\Foo.dll")).isEqualTo("Foo"),
                () -> assertThat(LibraryNames.nameOf(".so")).as("an extension alone is a name").isEqualTo(".so"),
                () -> assertThat(LibraryNames.nameOf("lib")).isEqualTo("lib"),
                () -> assertThat(LibraryNames.nameOf("libx")).isEqualTo("x"));
    }

    @Test
    void matchesExactlyOrWithWildcards() {
        assertAll(
                () -> assertThat(LibraryNames.matches("foo", "libfoo.so")).isTrue(),
                () -> assertThat(LibraryNames.matches("fo", "libfoo.so")).isFalse(),
                () -> assertThat(LibraryNames.matches("foo", "libfo.so")).isFalse(),
                () -> assertThat(LibraryNames.matches("*", "libfoo.so")).isTrue(),
                () -> assertThat(LibraryNames.matches("f*o", "libfoo.so")).isTrue(),
                () -> assertThat(LibraryNames.matches("f*x", "libfoo.so")).isFalse(),
                () -> assertThat(LibraryNames.matches("foo*", "libfoo.so")).as("* may be empty").isTrue(),
                () -> assertThat(LibraryNames.matches("**o", "libfoo.so")).isTrue(),
                () -> assertThat(LibraryNames.matches("*ab*ab", "xabxabab")).as("goes back to the last *").isTrue(),
                () -> assertThat(LibraryNames.matches("foo", "foo")).isTrue(),
                () -> assertThat(LibraryNames.matches("foox", "foo")).isFalse(),
                () -> assertThat(LibraryNames.matches("", "")).isTrue());
    }
}
