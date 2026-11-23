package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                () -> assertThat(LibraryLoads.askingClass(frame("java.lang.System", "loadLibrary"),
                        frame("jdk.internal.reflect.DirectMethodHandleAccessor", "invoke"),
                        frame("java.lang.reflect.Method", "invoke"), frame("com.example.Loader", "load")))
                        .as("through reflection").isEqualTo("com.example.Loader"),
                () -> assertThat(LibraryLoads.askingClass(frame("java.lang.Runtime", "loadLibrary"),
                        frame("sun.reflect.NativeMethodAccessorImpl", "invoke0"),
                        frame("sun.reflect.DelegatingMethodAccessorImpl", "invoke"),
                        frame("java.lang.invoke.LambdaForm$MH/0x1", "invoke"),
                        frame("java.lang.System", "loadLibrary"), frame("com.example.Loader", "load")))
                        .as("through reflection on JDK 8, and System calling Runtime")
                        .isEqualTo("com.example.Loader"),
                () -> assertThat(LibraryLoads.askingClass(frame("com.example.Main", "main"))).isEmpty(),
                () -> assertThat(LibraryLoads.askingClass()).isEmpty());
    }

    @Test
    void knowsTheJdksOwnClassesByTheirClassLoader() {
        assertAll(
                () -> assertThat(LibraryLoads.isJdks("java.lang.String")).isTrue(),
                () -> assertThat(LibraryLoads.isJdks("sun.nio.ch.SocketChannelImpl")).isTrue(),
                () -> assertThat(LibraryLoads.isJdks("javax.naming.InitialContext")).isTrue(),
                () -> assertThat(LibraryLoads.isJdks("java.sql.Connection")).as("the platform loader's").isTrue(),
                () -> assertThat(LibraryLoads.isJdks(LibraryLoadsTest.class.getName())).as("the class path's")
                        .isFalse(),
                () -> assertThat(LibraryLoads.isJdks("com.sun.jna.Native")).as("not the JDK's, whatever its package")
                        .isFalse(),
                () -> assertThat(LibraryLoads.isJdks("")).isFalse());
    }

    @Test
    void countsAFileInTheJdksOwnLibraryFolderAsTheJdks() throws IOException {
        final Path jdkLibraries = Path.of(System.getProperty("sun.boot.library.path").split(File.pathSeparator)[0]);
        final Path zip = jdkLibraries.resolve(System.mapLibraryName("zip"));
        final Path elsewhere = Files.createTempFile("libairgap", ".so");

        assertAll(
                () -> assertThat(LibraryLoads.isJdksFile("zip", true)).as("a name the JDK's folder has").isTrue(),
                () -> assertThat(LibraryLoads.isJdksFile("airgapexample", true)).isFalse(),
                () -> assertThat(LibraryLoads.isJdksFile(zip.toString(), false)).as("a path into it").isTrue(),
                () -> assertThat(LibraryLoads.isJdksFile(jdkLibraries.resolve("..").resolve(jdkLibraries.getFileName())
                        .resolve(zip.getFileName()).toString(), false)).as("however it's written").isTrue(),
                () -> assertThat(LibraryLoads.isJdksFile(elsewhere.toString(), false)).as("a path elsewhere").isFalse(),
                () -> assertThat(LibraryLoads.isJdksFile("zip", false)).as("a name read as a path").isFalse(),
                () -> assertThat(LibraryLoads.isJdksFile(jdkLibraries.resolve("libnothing.so").toString(), false))
                        .as("no such file").isFalse());
        Files.delete(elsewhere);
    }
}
