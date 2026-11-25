package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// The * matching that native: and process: allow entries use: each * stands for any run of characters, including
// none, and the pieces between them must appear in order, without overlapping.
class LibraryNamesWildcardTest {

    @ParameterizedTest(name = "{0} matches {1}")
    @CsvSource({
        "sqlite-*-libsqlitejdbc.jnilib, sqlite-3.36.0.3-1a2b-libsqlitejdbc.jnilib",
        "netty_*, netty_",
        "*, anything",
        "a*b*c, abc",
        "a*b*c, a-b-b-c",
        "lib*-*-x64.so, libnetty-1.2-x64.so",
        "*.dll, conscrypt.dll",
    })
    void matches(final String pattern, final String name) {
        assertThat(LibraryNames.wildcardMatch(pattern, name)).isTrue();
    }

    @ParameterizedTest(name = "{0} doesn't match {1}")
    @CsvSource({
        "sqlite-*-libsqlitejdbc.jnilib, sqlite-3.36-libsqlitejdbc.dylib",
        "netty, netty_native",
        "a*b*c, acb",
        "a*b*c, ab",
        "ab*ba, aba",
        "abc*cde, abcde",
        "lib*-*-x64.so, lib-x64.so",
        "x*, ax",
    })
    void doesNotMatch(final String pattern, final String name) {
        assertThat(LibraryNames.wildcardMatch(pattern, name)).isFalse();
    }
}
