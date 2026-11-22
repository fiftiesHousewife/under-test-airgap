package example.airgap.routes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Every class file of the running JDK: its modules from JDK 9, its rt.jar on JDK 8. */
final class JdkClassFiles {

    private JdkClassFiles() {
    }

    static void forEach(final Consumer<byte[]> use) throws IOException {
        final File home = new File(System.getProperty("java.home"));
        if (new File(home, "lib/modules").isFile()) {
            fromModules(use);
        } else {
            final File rt = new File(home, "lib/rt.jar");
            fromJar(rt.isFile() ? rt : new File(home, "jre/lib/rt.jar"), use);
        }
    }

    private static void fromModules(final Consumer<byte[]> use) throws IOException {
        final FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        try (Stream<Path> files = Files.walk(jrt.getPath("/modules"))) {
            files.filter(path -> path.toString().endsWith(".class")).forEach(path -> use.accept(read(path)));
        }
    }

    private static byte[] read(final Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void fromJar(final File jar, final Consumer<byte[]> use) throws IOException {
        try (JarFile classes = new JarFile(jar)) {
            final Enumeration<JarEntry> entries = classes.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    try (InputStream in = classes.getInputStream(entry)) {
                        use.accept(readAll(in));
                    }
                }
            }
        }
    }

    private static byte[] readAll(final InputStream in) throws IOException {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
