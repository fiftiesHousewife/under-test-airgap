plugins {
    id("io.github.fiftieshousewife.java-conventions")
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
    id("io.github.fiftieshousewife.airgap-publishing")
}

// The Gradle wiring: a plugin that starts every Test and JavaExec task (and so bootRun) with the agent. It runs inside
// old projects' builds, so it's Java 8 bytecode compiled against the oldest Gradle API it supports (see
// docs/decisions/0010-the-gradle-wiring-supports-gradle-5-6-and-later-proved-on-each-major-version.md), and the
// integration tests run a sample build on the latest release of each supported major version.
group = "io.github.fiftieshousewife.airgap"
base.archivesName = "airgap-gradle-plugin"

// A Gradle plugin uses Gradle's own logging; nothing from the conventions' logging set goes into its POM.
configurations.named("implementation") { dependencies.removeIf { it.group == "org.slf4j" } }
configurations.named("runtimeOnly") { dependencies.removeIf { it.group == "org.apache.logging.log4j" } }

// java-gradle-plugin adds the API of the Gradle running this build; the plugin is compiled against the oldest it
// supports instead, so a newer API can't slip in.
listOf("api", "compileOnlyApi", "compileOnly", "implementation").forEach { name ->
    configurations.findByName(name)?.dependencies?.removeIf { it is FileCollectionDependency }
}

gradlePlugin {
    website = "https://github.com/fiftiesHousewife/under-test-airgap"
    vcsUrl = "https://github.com/fiftiesHousewife/under-test-airgap.git"
    plugins {
        create("airgap") {
            id = "io.github.fiftieshousewife.airgap"
            implementationClass = "io.github.fiftieshousewife.airgap.gradle.AirgapPlugin"
            displayName = "airgap"
            description = "Starts every test run and application start (including Spring Boot's bootRun) with the " +
                "airgap agent, which refuses and reports calls to other machines, and gives the tests its JUnit listener."
            tags = listOf("testing", "security", "network", "java-agent", "legacy-code")
        }
    }
}

dependencies {
    compileOnly(libs.gradle.api.oldest.supported)
    compileOnly(libs.spotbugs.annotations)
    testCompileOnly(libs.spotbugs.annotations)
    testImplementation(gradleApi())
}

tasks.named<JavaCompile>("compileJava") {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

// Gradle's ProjectBuilder defines classes in java.lang's class loader, which JDK 17 and later allow only when opened.
tasks.test {
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("**/airgap-plugin.properties") { expand("version" to version) }
}

// ---------- Integration tests: a sample build under the plugin, on each supported Gradle ----------
val integrationTest: SourceSet = sourceSets.create("integrationTest")
val sampleTestLibraries: Configuration = configurations.create("sampleTestLibraries") {
    isCanBeConsumed = false
}
dependencies {
    "integrationTestImplementation"(gradleTestKit())
    "integrationTestCompileOnly"(libs.spotbugs.annotations)
    "integrationTestImplementation"(platform(libs.junit.bom))
    "integrationTestImplementation"(libs.junit.jupiter)
    "integrationTestImplementation"(libs.assertj.core)
    "integrationTestRuntimeOnly"(libs.junit.platform.launcher)
    // The sample build's tests use JUnit 4, handed to it as files so it needs no package repository but the local one.
    sampleTestLibraries(libs.junit4)
}

// Each Gradle runs on a JDK it supports; the newest is the Gradle running this build.
val gradleOnJdk = libs.versions.gradle.tested.get().split(",").zip(listOf("8", "11", "17", "21"))
fun jdkHome(jdk: String): Provider<String> {
    val given = providers.gradleProperty("airgap.jdk$jdk")
    // A compiler, not only a launcher: the sample build compiles code, and a Java runtime alone (such as macOS's
    // browser plug-in) would be found as a launcher.
    return if (given.isPresent) given else javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(jdk) }
        .map { it.metadata.installationPath.asFile.absolutePath }
}
val integrationRepo = rootProject.layout.buildDirectory.dir("integration-repo")

val integrationTestTask = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs a sample build under the plugin on the latest release of each supported major Gradle version."
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    dependsOn(":agent:publishAllPublicationsToIntegrationRepository",
        ":junit:publishAllPublicationsToIntegrationRepository", "publishAllPublicationsToIntegrationRepository")
    inputs.files(sampleTestLibraries)
    val homes = gradleOnJdk.map { (gradle, jdk) -> jdkHome(jdk).map { "$gradle=$it" } }
    val currentJdk = jdkHome("25")
    val repo = integrationRepo
    val libraries = files(sampleTestLibraries)
    val version = project.version.toString()
    val distributions = providers.gradleProperty("airgap.gradleDistributions")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOfNotNull(
            "-Dairgap.it.gradleVersions=${homes.joinToString(",") { it.get() }}",
            "-Dairgap.it.currentJdk=${currentJdk.get()}",
            "-Dairgap.it.repo=${repo.get().asFile.absolutePath}",
            "-Dairgap.it.version=$version",
            "-Dairgap.it.junit=${libraries.files.joinToString(File.pathSeparator) { it.absolutePath }}",
            distributions.orNull?.let { "-Dairgap.it.distributions=$it" },
        )
    })
}
tasks.check {
    dependsOn(integrationTestTask)
}

// java-gradle-plugin's own publications: the plugin's jar, and the marker Gradle looks for when a build asks for the
// plugin by id. Both go to Maven Central; the Gradle Plugin Portal gets the plugin from publishPlugins.
publishing.publications.withType<MavenPublication>().configureEach {
    if (name == "pluginMaven") {
        artifactId = "airgap-gradle-plugin"
        pom {
            name = "airgap-gradle-plugin"
            description = "Starts every Gradle test run and application start with the airgap agent."
        }
    } else {
        pom {
            name = "airgap-gradle-plugin marker"
            description = "Points the plugin id io.github.fiftieshousewife.airgap at airgap-gradle-plugin."
        }
    }
}
