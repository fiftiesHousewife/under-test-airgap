package io.github.fiftieshousewife.airgap.gradle;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Writes the sample build: one class that makes a host lookup, and one JUnit 4 test that makes another. */
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Test code: the paths come from this build")
final class SampleBuild {

    private SampleBuild() {
    }

    static void write(final Path folder, final String jdk) throws IOException {
        final String repo = uri(System.getProperty("airgap.it.repo"));
        write(folder, "gradle.properties", "org.gradle.java.home=" + jdk.replace("\\", "/") + "\n");
        write(folder, "settings.gradle", "pluginManagement { repositories { maven { url '" + repo + "' } } }\n"
                + "rootProject.name = 'sample'\n");
        write(folder, "build.gradle", buildScript(repo));
        write(folder, "src/main/java/sample/Main.java", "package sample;\n"
                + "public class Main {\n"
                + "    public static void main(String[] args) {\n"
                + "        try {\n"
                + "            java.net.InetAddress.getByName(\"sample-main.invalid\");\n"
                + "        } catch (java.net.UnknownHostException e) {\n"
                + "            System.out.println(e.getMessage());\n"
                + "        }\n"
                + "    }\n"
                + "}\n");
        write(folder, "src/test/java/sample/SampleTest.java", "package sample;\n"
                + "public class SampleTest {\n"
                + "    @org.junit.Test\n"
                + "    public void lookupIsRefused() {\n"
                + "        try {\n"
                + "            java.net.InetAddress.getByName(\"sample-test.invalid\");\n"
                + "            org.junit.Assert.fail(\"the lookup went ahead\");\n"
                + "        } catch (java.net.UnknownHostException e) {\n"
                + "            org.junit.Assert.assertTrue(e.getMessage(), e.getMessage().contains(\"airgap\"));\n"
                + "        }\n"
                + "    }\n"
                + "}\n");
    }

    private static String buildScript(final String repo) {
        final StringBuilder junit = new StringBuilder();
        for (final String jar : System.getProperty("airgap.it.junit").split(File.pathSeparator)) {
            junit.append(junit.length() == 0 ? "" : ", ").append('\'').append(jar.replace("\\", "/")).append('\'');
        }
        return "plugins {\n"
                + "    id 'java'\n"
                + "    id 'io.github.fiftieshousewife.airgap' version '"
                + System.getProperty("airgap.it.version") + "'\n"
                + "}\n"
                + "repositories { maven { url '" + repo + "' } }\n"
                + "dependencies { testImplementation files(" + junit + ") }\n"
                + "airgap { packages.add('sample.') }\n"
                + "task runSample(type: JavaExec) {\n"
                + "    classpath = sourceSets.main.runtimeClasspath\n"
                + "    if (org.gradle.util.GradleVersion.current() >= org.gradle.util.GradleVersion.version('6.4')) {\n"
                + "        mainClass.set('sample.Main')\n"
                + "    } else {\n"
                + "        main = 'sample.Main'\n"
                + "    }\n"
                + "}\n";
    }

    private static String uri(final String path) {
        return new File(path).toURI().toString();
    }

    private static void write(final Path folder, final String path, final String text) throws IOException {
        final Path file = folder.resolve(path);
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
