pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        // The shared code-quality standards (https://github.com/fiftiesHousewife/under-test-java-conventions).
        id("io.github.fiftieshousewife.java-conventions") version "0.3.0"
    }
}

plugins {
    // Where Gradle downloads a JDK if none is installed. Without internet access, switch downloads off
    // (org.gradle.java.installations.auto-download=false) and point Gradle at installed JDKs
    // (org.gradle.java.installations.paths).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "under-test-airgap"

// Each directory that holds a build.gradle.kts becomes a project, named by its path:
//   maintainer-tools/<id>  -> :maintainer-tools:<id>  (repo checks)
//   agent  -> :agent  (the airgap agent: refuses and reports risky calls)
//   junit  -> :junit  (the JUnit Platform listener and @AllowCalls)
//   gradle-plugin  -> :gradle-plugin  (the Gradle wiring)
fun includeAll(root: String) {
    rootDir.resolve(root).listFiles()?.sorted()?.forEach { dir ->
        if (dir.resolve("build.gradle.kts").isFile) {
            val path = ":$root:${dir.name}"
            include(path)
            project(path).projectDir = dir
        }
    }
}
includeAll("maintainer-tools")
include(":agent", ":junit", ":gradle-plugin", ":maven-extension")
