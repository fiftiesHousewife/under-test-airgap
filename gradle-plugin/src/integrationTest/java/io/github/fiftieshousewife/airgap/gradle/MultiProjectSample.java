package io.github.fiftieshousewife.airgap.gradle;

import java.io.IOException;
import java.nio.file.Path;

// Writes a multi-project sample build with shared convention plugins and no root build file: the conventions come from
// an included build-logic build (Gradle 7 and later) or buildSrc (Gradle 5 and 6, which can't include plugin builds);
// two subprojects use them, and only app applies the airgap plugin.
final class MultiProjectSample {

    private static final String CONVENTIONS = "package sample;\n"
            + "import org.gradle.api.Plugin;\n"
            + "import org.gradle.api.Project;\n"
            + "public class JavaConventions implements Plugin<Project> {\n"
            + "    public void apply(Project project) {\n"
            + "        project.getPluginManager().apply(\"java\");\n"
            + "        project.getRepositories().maven(repo -> repo.setUrl(\"REPOSITORY\"));\n"
            + "        project.getDependencies().add(\"testImplementation\", project.files(JUNIT_JARS));\n"
            + "    }\n"
            + "}\n";
    private static final String CONVENTIONS_BUILD = "plugins { id 'java-gradle-plugin' }\n"
            + "gradlePlugin { plugins { conventions {\n"
            + "    id = 'sample.java-conventions'\n"
            + "    implementationClass = 'sample.JavaConventions'\n"
            + "} } }\n";
    private static final String APP_TEST = "package sample;\n"
            + "public class AppTest {\n"
            + "    @org.junit.Test\n"
            + "    public void lookupIsRefused() {\n"
            + "        try {\n"
            + "            java.net.InetAddress.getByName(\"app-test.invalid\");\n"
            + "        } catch (java.net.UnknownHostException expected) {\n"
            + "            // refused by the agent\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private MultiProjectSample() {
    }

    static void write(final Path folder, final String jdk, final String gradle) throws IOException {
        final boolean includedBuild = !gradle.startsWith("5.") && !gradle.startsWith("6.");
        final String logic = includedBuild ? "build-logic" : "buildSrc";
        SampleBuild.write(folder, "gradle.properties", "org.gradle.java.home=" + jdk.replace("\\", "/") + "\n");
        SampleBuild.write(folder, "settings.gradle", "pluginManagement {\n"
                + (includedBuild ? "    includeBuild('build-logic')\n" : "")
                + "    repositories { maven { url '" + SampleBuild.repo() + "' } }\n}\n"
                + "rootProject.name = 'multi'\ninclude 'app', 'lib'\n");
        SampleBuild.write(folder, logic + "/settings.gradle", "rootProject.name = '" + logic + "'\n");
        SampleBuild.write(folder, logic + "/build.gradle", CONVENTIONS_BUILD);
        SampleBuild.write(folder, logic + "/src/main/java/sample/JavaConventions.java",
                CONVENTIONS.replace("REPOSITORY", SampleBuild.repo())
                        .replace("JUNIT_JARS", SampleBuild.junitJars().replace('\'', '"')));
        writeSubprojects(folder);
    }

    private static void writeSubprojects(final Path folder) throws IOException {
        SampleBuild.write(folder, "lib/build.gradle", "plugins { id 'sample.java-conventions' }\n");
        SampleBuild.write(folder, "lib/src/test/java/sample/LibTest.java", APP_TEST.replace("AppTest", "LibTest")
                .replace("app-test", "lib-test"));
        SampleBuild.write(folder, "app/build.gradle", "plugins {\n    id 'sample.java-conventions'\n"
                + "    id 'io.github.fiftieshousewife.airgap' version '" + System.getProperty("airgap.it.version")
                + "'\n}\n");
        SampleBuild.write(folder, "app/src/test/java/sample/AppTest.java", APP_TEST);
    }
}
