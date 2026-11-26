plugins {
    id("io.github.fiftieshousewife.java-conventions")
}

// The example old services in this folder, run end to end under the agent: each build's report summary must match its
// saved, approved one (examples/<example>/approved/). Run with -PapproveExamples to approve the summaries a run makes,
// after reading them. The Gradle examples run through TestKit with this repo's plugin, agent and listener, published
// to the build's local repository; the Maven one runs on the Maven the extension's tests unpack, with the extension.
dependencies {
    testImplementation(gradleTestKit())
    testCompileOnly(libs.spotbugs.annotations)
}

tasks.test {
    val repo = rootProject.layout.buildDirectory.dir("integration-repo").get().asFile.toURI().toString()
    val mavenVersion = "3.9.16"
    dependsOn(":agent:publishAllPublicationsToIntegrationRepository",
        ":junit:publishAllPublicationsToIntegrationRepository",
        ":gradle-plugin:publishAllPublicationsToIntegrationRepository",
        ":maven-extension:installForIntegrationTest", ":maven-extension:unpackMaven3916")
    inputs.files(fileTree(layout.projectDirectory) { include("*/**"); exclude("build/**", "src/**", ".gradle/**") })
        .withPropertyName("examples").withPathSensitivity(PathSensitivity.RELATIVE)
    val javaHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
        .map { it.metadata.installationPath.asFile.absolutePath }
    val examples = layout.projectDirectory.asFile.absolutePath
    val mavenHome = project(":maven-extension").layout.buildDirectory
        .dir("mavens/$mavenVersion/apache-maven-$mavenVersion").get().asFile.absolutePath
    val mavenRepo = project(":maven-extension").layout.buildDirectory.dir("maven-it-repo").get().asFile.absolutePath
    val version = project.version.toString()
    val approve = providers.gradleProperty("approveExamples").isPresent
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dairgap.examples=$examples",
            "-Dairgap.examples.repo=$repo",
            "-Dairgap.examples.version=$version",
            "-Dairgap.examples.javaHome=${javaHome.get()}",
            "-Dairgap.examples.mavenHome=$mavenHome",
            "-Dairgap.examples.mavenRepo=$mavenRepo",
            "-Dairgap.examples.approve=$approve",
        )
    })
    if (approve) {
        outputs.upToDateWhen { false }
    }
}
