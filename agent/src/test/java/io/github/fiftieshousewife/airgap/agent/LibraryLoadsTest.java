package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class LibraryLoadsTest {

    private static StackTraceElement frame(final String type, final String method) {
        return new StackTraceElement(type, method, null, -1);
    }

    @Test
    void namesTheClassThatCalledLoadLibrary() {
        assertAll(
                () -> assertThat(LibraryLoads.askingClass(frame(Gate.class.getName(), "nativeLibrary"),
                        frame("java.lang.System", "loadLibrary"), frame("io.netty.util.NativeLoader", "load"),
                        frame("io.netty.Bootstrap", "connect"))).isEqualTo("io.netty.util.NativeLoader"),
                () -> assertThat(LibraryLoads.askingClass(frame("java.lang.Runtime", "load"),
                        frame("org.sqlite.SQLiteJDBCLoader", "loadNativeLibrary")))
                        .isEqualTo("org.sqlite.SQLiteJDBCLoader"),
                () -> assertThat(LibraryLoads.askingClass(frame("com.example.Main", "main"))).isEmpty(),
                () -> assertThat(LibraryLoads.askingClass()).isEmpty());
    }

    @Test
    void knowsTheJdksOwnClasses() {
        assertAll(
                () -> assertThat(LibraryLoads.isJdks("java.net.AbstractPlainSocketImpl$1")).isTrue(),
                () -> assertThat(LibraryLoads.isJdks("sun.security.ec.SunEC")).isTrue(),
                () -> assertThat(LibraryLoads.isJdks("jdk.net.MacOSXSocketOptions")).isTrue(),
                () -> assertThat(LibraryLoads.isJdks("io.netty.util.internal.NativeLibraryLoader")).isFalse(),
                () -> assertThat(LibraryLoads.isJdks("")).isFalse());
    }
}
