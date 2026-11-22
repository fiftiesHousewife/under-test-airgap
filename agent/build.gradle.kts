import io.github.fiftieshousewife.airgap.build.runWithOwnAgent

plugins {
    id("io.github.fiftieshousewife.java-conventions")
    id("info.solidsoft.pitest")
    alias(libs.plugins.shadow)
    id("io.github.fiftieshousewife.airgap-publishing")
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
    filter {
        excludeTestsMatching("*AgentTestReportsMatchSchemaTest")
        excludeTestsMatching("*OsTraceMatchesReportsTest")
    }
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
val agentJarElements: Configuration = configurations.create("agentJarElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts {
    add(agentJarElements.name, tasks.shadowJar)
}

// ---------- The agent's own tests: every hook, with the agent attached, on every supported JDK ----------
// Compiled for Java 8 and run on JUnit 5, so the same tests run on JDK 8 and on every JDK since.
val agentTest: SourceSet = sourceSets.create("agentTest")

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
        runWithOwnAgent(files(agentJar), reportFile, "allow=allowed.example.invalid:8443,lookup-once.example.invalid,process:java,native:airgapallowed")
    }
    tasks.check {
        dependsOn(task)
    }
}

// The JDK's own routes: on every JDK, its classes are read, and every public method that can reach a risky call without
// passing a hook must be on a reviewed list. Run without the agent: the hooks are read from Hook, as the self-check does.
val jdkRoutes: SourceSet = sourceSets.create("jdkRoutes")
tasks.named<JavaCompile>(jdkRoutes.compileJavaTaskName) {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}
dependencies {
    "jdkRoutesImplementation"(sourceSets.main.get().output)
    "jdkRoutesImplementation"(libs.byte.buddy)
    "jdkRoutesImplementation"(platform(libs.junit5.bom))
    "jdkRoutesImplementation"(libs.junit5.jupiter.api)
    "jdkRoutesImplementation"(libs.assertj.core)
    "jdkRoutesCompileOnly"(libs.spotbugs.annotations)
    "jdkRoutesRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
    "jdkRoutesRuntimeOnly"(libs.junit5.platform.launcher)
}
testJdks.get().forEach { jdk ->
    val task = tasks.register<Test>("jdkRoutesOnJdk$jdk") {
        group = "verification"
        description = "Checks every public route in JDK $jdk to a risky call passes a hook, or is reviewed."
        testClassesDirs = jdkRoutes.output.classesDirs
        classpath = jdkRoutes.runtimeClasspath
        useJUnitPlatform()
        maxHeapSize = "2g"
        val jdkHome = providers.gradleProperty("airgap.jdk$jdk")
        if (jdkHome.isPresent) {
            executable = File(jdkHome.get(), "bin/java").absolutePath
        } else {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(jdk) }
        }
        systemProperty("airgap.routes.out", layout.buildDirectory.file("jdk-routes/jdk$jdk.tsv").get().asFile.absolutePath)
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

// What the operating system saw the agent's tests do must match what the agent reported: any connection or packet to
// another machine, program started or library opened that the agent didn't report as allowed is a way round it. CI
// runs the agent's tests under strace (maintainer-tools/os-trace/wrap-jdks.sh) and then this, with
// -Pairgap.osTrace=<the folder of traces>.
val osTrace = providers.gradleProperty("airgap.osTrace").map { rootProject.file(it) }
tasks.register<Test>("checkOsTrace") {
    group = "verification"
    description = "Checks the strace records of the agent's own tests against the agent's reports (with airgap.osTrace)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*OsTraceMatchesReportsTest") }
    systemProperty("airgap.osTrace", osTrace.map { it.absolutePath }.getOrElse(""))
    systemProperty("airgap.agentTestReports", layout.buildDirectory.dir("airgap-tests").get().asFile.absolutePath)
    dependsOn(tasks.withType<Test>().matching { it.name.startsWith("agentTestOnJdk") })
    enabled = osTrace.isPresent
    if (osTrace.isPresent) {
        inputs.dir(osTrace).withPropertyName("osTrace")
    }
    inputs.dir(layout.buildDirectory.dir("airgap-tests")).withPropertyName("agentTestReports").optional()
}

// Coverage is measured by the unit tests, which run the agent's logic directly. The classes that only make sense with
// the agent attached (its entry point, its hooks and its self-check) are proved by the agent's own tests above, on
// every JDK, instead.
val attachedOnly = listOf(
    "**/agent/AirgapAgent*", "**/agent/AgentStart*", "**/agent/Hooks*", "**/agent/SelfCheck*", "**/agent/advice/**",
    "**/agent/Gate*", "**/agent/RunningTests*", "**/agent/NativeGate*", "**/agent/SctpGate*",
)
listOf(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification).forEach { task ->
    task.configure {
        classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(attachedOnly) } }))
    }
}

// Mutation testing: PIT plants bugs in the code that decides, refuses and reports each call, and the unit tests must
// catch nearly all of them. The classes that only run with the agent attached are left out, as for coverage.
pitest {
    pitestVersion = libs.versions.pitest.asProvider()
    junit5PluginVersion = libs.versions.pitest.junit5.get()
    targetClasses = listOf("io.github.fiftieshousewife.airgap.agent.*")
    excludedClasses = listOf(
        "io.github.fiftieshousewife.airgap.agent.AirgapAgent*", "io.github.fiftieshousewife.airgap.agent.AgentStart*",
        "io.github.fiftieshousewife.airgap.agent.Hooks*", "io.github.fiftieshousewife.airgap.agent.SelfCheck*",
        "io.github.fiftieshousewife.airgap.agent.advice.*", "io.github.fiftieshousewife.airgap.agent.Gate*",
        "io.github.fiftieshousewife.airgap.agent.RunningTests*", "io.github.fiftieshousewife.airgap.agent.NativeGate*",
        "io.github.fiftieshousewife.airgap.agent.SctpGate*",
    )
    excludedTestClasses = listOf("*AgentTestReportsMatchSchemaTest", "*OsTraceMatchesReportsTest")
    jvmArgs = listOf("-Dairgap.schema=${reportSchema.asFile.absolutePath}")
    outputFormats = listOf("HTML", "XML")
    timestampedReports = false
    mutationThreshold = libs.versions.min.mutation.score.map { it.toInt() }
}
tasks.check {
    dependsOn(tasks.named("pitest"))
}

publishing {
    publications {
        create<MavenPublication>("agent") {
            artifactId = "airgap-agent"
            artifact(tasks.shadowJar)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
            pom {
                name = "airgap-agent"
                description = "A Java agent that refuses risky calls (network, host lookups, processes) inside the JVM, " +
                    "as an unplugged machine would, and reports each one with the code that made it."
            }
        }
    }
}
