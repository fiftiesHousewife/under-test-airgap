plugins {
    id("io.github.fiftieshousewife.java-conventions")
    `maven-publish`
}

// The JUnit Platform listener (names the test behind each call) and @AllowCalls. Built against the JUnit 5 API, for
// Java 8, so it runs on JUnit 5 and JUnit 6 alike. It talks to the agent by reflection, and does nothing without it.
group = "io.github.fiftieshousewife.airgap"
base.archivesName = "airgap-junit"

dependencies {
    compileOnly(platform(libs.junit5.bom))
    compileOnly(libs.junit5.jupiter.api)
    compileOnly(libs.junit5.platform.launcher)
    testCompileOnly(project(":agent"))
}

tasks.named<JavaCompile>("compileJava") {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

// The tests run with the agent attached, as a project using this jar would.
val agent: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    agent(project(mapOf("path" to ":agent", "configuration" to "agentJarElements")))
}

tasks.test {
    val agentFiles = files(agent)
    val report = layout.buildDirectory.file("airgap-tests/calls.jsonl")
    inputs.files(agentFiles)
    doFirst { report.get().asFile.delete() }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-javaagent:${agentFiles.singleFile.absolutePath}=report=${report.get().asFile.absolutePath}" +
                ";project=io.github.fiftieshousewife.airgap.junit",
            "-Dairgap.test.report=${report.get().asFile.absolutePath}",
        )
    })
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
                url = "https://github.com/fiftiesHousewife/under-test-airgap"
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
            }
        }
    }
}
