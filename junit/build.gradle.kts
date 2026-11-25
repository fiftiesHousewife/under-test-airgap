import io.github.fiftieshousewife.airgap.build.runWithOwnAgent

plugins {
    id("io.github.fiftieshousewife.java-conventions")
    id("io.github.fiftieshousewife.airgap-publishing")
}

// The JUnit Platform listener (names the test behind each call) and @AllowCalls. Built against the JUnit 5 API, for
// Java 8, so it runs on JUnit 5 and JUnit 6 alike. It talks to the agent by reflection, and does nothing without it.
group = "io.github.fiftieshousewife.airgap"
base.archivesName = "airgap-junit"

dependencies {
    compileOnly(platform(libs.junit5.bom))
    compileOnly(libs.junit5.jupiter.api)
    compileOnly(libs.junit5.platform.launcher)
    // JUnit 4 itself, for the listener Surefire's JUnit 4 provider calls; a project running JUnit 4 has it already.
    compileOnly(libs.junit4)
    testImplementation(libs.junit4)
    testCompileOnly(project(":agent"))
}

tasks.named<JavaCompile>("compileJava") {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

// The tests run with the agent attached, as a project using this jar would.
val agent: Configuration = configurations.create("agent") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    agent(project(mapOf("path" to ":agent", "configuration" to "agentJarElements")))
}

tasks.test {
    runWithOwnAgent(files(agent), layout.buildDirectory.file("airgap-tests/calls.jsonl"),
        "project=io.github.fiftieshousewife.airgap.junit")
}

publishing {
    publications {
        create<MavenPublication>("junit") {
            artifactId = "airgap-junit"
            from(components["java"])
            pom {
                name = "airgap-junit"
                description = "The airgap agent's JUnit Platform listener, which names the test behind each call, and " +
                    "@AllowCalls, which lets a test allow a stand-in."
            }
        }
    }
}
