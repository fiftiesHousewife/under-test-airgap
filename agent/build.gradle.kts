plugins {
    id("io.github.fiftieshousewife.java-conventions")
    alias(libs.plugins.shadow)
    `maven-publish`
}

// The airgap agent runs inside the projects it guards, on JDK 8 and later: Java 8 bytecode, and nothing in its jar but
// Byte Buddy, relocated under the agent's own package so it never meets the copy Mockito brings.
group = "io.github.fiftieshousewife.airgap"
base.archivesName = "airgap-agent"

dependencies {
    implementation(libs.byte.buddy)
    compileOnly(libs.spotbugs.annotations)
    testCompileOnly(libs.spotbugs.annotations)
    // Every line the agent writes is checked against schema/airgap-report.schema.json.
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.jackson3.yaml)
}

val reportSchema = rootProject.layout.projectDirectory.file("schema/airgap-report.schema.json")
tasks.test {
    systemProperty("airgap.schema", reportSchema.asFile.absolutePath)
    filter { excludeTestsMatching("*AgentTestReportsMatchSchemaTest") }
}

tasks.named<JavaCompile>("compileJava") {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("net.bytebuddy", "io.github.fiftieshousewife.airgap.shaded.bytebuddy")
    dependencies {
        include(dependency("net.bytebuddy:byte-buddy"))
    }
    exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/maven/**", "META-INF/LICENSE*",
        "META-INF/NOTICE*")
    manifest {
        attributes(
            "Premain-Class" to "io.github.fiftieshousewife.airgap.agent.AirgapAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Implementation-Title" to "airgap-agent",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// The agent jar, for other projects in this build (the JUnit part's tests) to attach.
val agentJarElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts {
    add(agentJarElements.name, tasks.shadowJar)
}

// ---------- The agent's own tests: every hook, with the agent attached, on every supported JDK ----------
// Compiled for Java 8 and run on JUnit 5, so the same tests run on JDK 8 and on every JDK since.
val agentTest: SourceSet by sourceSets.creating

tasks.named<JavaCompile>(agentTest.compileJavaTaskName) {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

dependencies {
    // The agent's classes, to compile against; at run time they come from the agent jar, on the bootstrap class path.
    "agentTestCompileOnly"(sourceSets.main.get().output)
    "agentTestCompileOnly"(libs.spotbugs.annotations)
    "agentTestImplementation"(platform(libs.junit5.bom))
    "agentTestImplementation"(libs.junit5.jupiter.api)
    "agentTestImplementation"(libs.assertj.core)
    "agentTestRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
    "agentTestRuntimeOnly"(libs.junit5.platform.launcher)
}

// Which JDKs the agent's tests run on: all supported by default; -Pairgap.testJdks=25 runs one. Each JDK comes from
// Gradle's toolchains, or from -Pairgap.jdk<version>=<JDK home> where a machine keeps its JDKs somewhere else.
val testJdks = providers.gradleProperty("airgap.testJdks").orElse("8,11,17,21,25")
    .map { list -> list.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
val agentJar = tasks.shadowJar.flatMap { it.archiveFile }

testJdks.get().forEach { jdk ->
    val reportFile = layout.buildDirectory.file("airgap-tests/jdk$jdk/calls.jsonl")
    val task = tasks.register<Test>("agentTestOnJdk$jdk") {
        group = "verification"
        description = "Runs the agent's tests on JDK $jdk, with the agent attached."
        testClassesDirs = agentTest.output.classesDirs
        classpath = agentTest.runtimeClasspath
        useJUnitPlatform()
        val jdkHome = providers.gradleProperty("airgap.jdk$jdk")
        if (jdkHome.isPresent) {
            executable = File(jdkHome.get(), "bin/java").absolutePath
        } else {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(jdk) }
        }
        inputs.file(agentJar)
        val report = reportFile
        val jar = agentJar
        doFirst { report.get().asFile.delete() }
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf(
                "-javaagent:${jar.get().asFile.absolutePath}=report=${report.get().asFile.absolutePath}" +
                    ";allow=allowed.example.invalid:8443,process:java",
                "-Dairgap.test.report=${report.get().asFile.absolutePath}",
                "-Dairgap.test.agent=${jar.get().asFile.absolutePath}",
            )
        })
    }
    tasks.check {
        dependsOn(task)
    }
}

// Every line the agent wrote during its own tests, on every JDK, must match the report's schema.
val checkAgentTestReports = tasks.register<Test>("checkAgentTestReports") {
    group = "verification"
    description = "Checks every report line the agent wrote during its own tests against the report's schema."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*AgentTestReportsMatchSchemaTest") }
    systemProperty("airgap.schema", reportSchema.asFile.absolutePath)
    systemProperty("airgap.agentTestReports", layout.buildDirectory.dir("airgap-tests").get().asFile.absolutePath)
    dependsOn(tasks.withType<Test>().matching { it.name.startsWith("agentTestOnJdk") })
}
tasks.check {
    dependsOn(checkAgentTestReports)
}

// Coverage is measured by the unit tests, which run the agent's logic directly. The classes that only make sense with
// the agent attached (its entry point, its hooks and its self-check) are proved by the agent's own tests above, on
// every JDK, instead.
val attachedOnly = listOf(
    "**/agent/AirgapAgent*", "**/agent/AgentStart*", "**/agent/Hooks*", "**/agent/SelfCheck*", "**/agent/advice/**",
    "**/agent/Gate*",
)
listOf(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification).forEach { task ->
    task.configure {
        classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(attachedOnly) } }))
    }
}

publishing {
    // A local repository for the Gradle wiring's integration tests, so their sample builds need no other.
    repositories {
        maven {
            name = "integration"
            url = uri(rootProject.layout.buildDirectory.dir("integration-repo"))
        }
    }
    publications {
        create<MavenPublication>("agent") {
            artifactId = "airgap-agent"
            artifact(tasks.shadowJar)
            pom {
                name = "airgap-agent"
                description = "A Java agent that refuses risky calls (network, host lookups, processes) inside the JVM, " +
                    "as an unplugged machine would, and reports each one with the code that made it."
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
