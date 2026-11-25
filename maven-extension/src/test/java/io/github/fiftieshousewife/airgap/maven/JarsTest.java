package io.github.fiftieshousewife.airgap.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarsTest {

    @TempDir
    Path folder;

    private Path jarWith(final String name, final String resource) throws IOException {
        final Path jar = folder.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(new ZipEntry(resource));
            out.write(new byte[] {(byte) 0xCA, (byte) 0xFE});
            out.closeEntry();
        }
        return jar;
    }

    @Test
    void findsEachJarOnTheExtensionsClassPath() throws IOException {
        final Path agent = jarWith("airgap agent.jar", Jars.AGENT_CLASS);
        try (URLClassLoader loader = new URLClassLoader(new URL[] {agent.toUri().toURL()}, null)) {
            assertAll(
                    () -> assertThat(Jars.find(loader, Jars.AGENT_CLASS, "airgap-agent").toPath()).isEqualTo(agent),
                    () -> assertThatIllegalStateException()
                            .isThrownBy(() -> Jars.find(loader, Jars.LISTENER_CLASS, "airgap-junit"))
                            .withMessageContaining("airgap-junit isn't on the extension's class path"));
        }
    }

    @Test
    void refusesAClassThatIsntInAJar() throws IOException {
        final Path classes = Files.createDirectories(folder.resolve("classes"));
        Files.createDirectories(Objects.requireNonNull(classes.resolve(Jars.AGENT_CLASS).getParent()));
        Files.write(classes.resolve(Jars.AGENT_CLASS), new byte[] {1});
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, null)) {
            assertThatIllegalStateException().isThrownBy(() -> Jars.find(loader, Jars.AGENT_CLASS, "airgap-agent"));
        }
    }
}
