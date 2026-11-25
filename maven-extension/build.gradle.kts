plugins {
    id("io.github.fiftieshousewife.java-conventions")
    id("io.github.fiftieshousewife.airgap-publishing")
}

// The Maven wiring: a core extension that adds the agent to every module's Surefire, Failsafe and run plugins, and the
// JUnit listener to its tests, without editing the pom (see docs/decisions/0020-maven-builds-get-the-agent-through-a-
// maven-core-extension-without-editing-the-pom.md). It runs inside old projects' builds, so it's Java 8 bytecode
// compiled against the oldest Maven API it supports; the integration tests run sample builds on the latest release of
// each supported Maven line.
group = "io.github.fiftieshousewife.airgap"
base.archivesName = "airgap-maven-extension"

dependencies {
    // Maven provides its own API, and SLF4J with it, to every extension.
    compileOnly(libs.maven.core)
    compileOnly(libs.javax.inject)
    compileOnly(libs.spotbugs.annotations)
    // The jars the extension adds to each build: they travel as its ordinary dependencies, so a security review sees
    // each one.
    runtimeOnly(project(mapOf("path" to ":agent", "configuration" to "agentJarElements")))
    runtimeOnly(project(":junit"))
    testImplementation(libs.maven.core)
    testCompileOnly(libs.spotbugs.annotations)
}

tasks.named<JavaCompile>("compileJava") {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

// The bill of materials names this repo's own projects by their Gradle names; a security review looks for the names
// they're published under.
tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
    val outputs = listOf(jsonOutput, xmlOutput)
    doLast {
        for (output in outputs) {
            val file = output.get().asFile
            var text = file.readText()
            for (part in listOf("agent", "junit")) {
                text = text.replace(".airgap/$part@", ".airgap/airgap-$part@")
                    .replace(Regex("(\"name\"\\s*:\\s*\"|<name>)$part(\"|</name>)"), "$1airgap-$part$2")
            }
            file.writeText(text)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenExtension") {
            artifactId = "airgap-maven-extension"
            from(components["java"])
            pom {
                name = "airgap-maven-extension"
                description = "A Maven core extension that starts every module's tests and application runs with the " +
                    "airgap agent, without editing the pom."
            }
        }
    }
}

// ---------- Integration tests: sample builds under the extension, on the latest release of each supported Maven ----
// Each Maven comes from Maven Central as an ordinary artifact, unpacked here; the samples' own plugins and libraries
// download into a local repository kept in the build folder between runs.
val integrationTest: SourceSet = sourceSets.create("integrationTest")
dependencies {
    "integrationTestCompileOnly"(libs.spotbugs.annotations)
    "integrationTestImplementation"(platform(libs.junit.bom))
    "integrationTestImplementation"(libs.junit.jupiter)
    "integrationTestImplementation"(libs.assertj.core)
    "integrationTestRuntimeOnly"(libs.junit.platform.launcher)
}

val mavenVersions = libs.versions.maven.tested.get().split(",")
val mavenHomes = mavenVersions.map { version ->
    val distribution = configurations.create("maven${version.replace(Regex("[^A-Za-z0-9]"), "")}") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(distribution.name, "org.apache.maven:apache-maven:$version:bin@zip")
    val unpack = tasks.register<Sync>("unpackMaven${version.replace(Regex("[^A-Za-z0-9]"), "")}") {
        from(provider { zipTree(distribution.singleFile) })
        into(layout.buildDirectory.dir("mavens/$version"))
    }
    version to unpack
}

// The extension, the agent and the listener, installed in the samples' local repository as Maven installs artifacts,
// for the sample that names the extension in .mvn/extensions.xml.
val localRepo = layout.buildDirectory.dir("maven-it-repo")
val agentJar = files(configurations.runtimeClasspath).filter { it.name.startsWith("airgap-agent") }
val listenerJar = files(configurations.runtimeClasspath).filter { it.name.startsWith("airgap-junit") }
val installForIntegrationTest = tasks.register<Copy>("installForIntegrationTest") {
    val v = project.version.toString()
    val pomTask = tasks.named<GenerateMavenPom>("generatePomFileForMavenExtensionPublication")
    into(localRepo.map { it.dir("io/github/fiftieshousewife/airgap") })
    from(tasks.jar) { into("airgap-maven-extension/$v"); rename { "airgap-maven-extension-$v.jar" } }
    from(pomTask.map { it.destination }) {
        into("airgap-maven-extension/$v")
        rename { "airgap-maven-extension-$v.pom" }
    }
    for ((artifact, jar) in listOf("airgap-agent" to agentJar, "airgap-junit" to listenerJar)) {
        from(jar) { into("$artifact/$v"); rename { "$artifact-$v.jar" } }
        from(resources.text.fromString(
            "<project><modelVersion>4.0.0</modelVersion><groupId>io.github.fiftieshousewife.airgap</groupId>" +
                "<artifactId>$artifact</artifactId><version>$v</version></project>"
        )) { into("$artifact/$v"); rename { "$artifact-$v.pom" } }
    }
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs sample builds under the extension on the latest release of each supported Maven line."
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    val extensionPath = files(tasks.jar, configurations.runtimeClasspath)
    inputs.files(extensionPath).withPropertyName("extension")
    val samples = layout.projectDirectory.dir("src/integrationTest/resources/samples")
    inputs.dir(samples).withPropertyName("samples")
    mavenHomes.forEach { (_, unpack) -> dependsOn(unpack) }
    dependsOn(installForIntegrationTest)
    val airgapVersion = project.version.toString()
    val repo = localRepo
    val homes = mavenHomes.map { (version, unpack) ->
        unpack.map { "$version=" + it.destinationDir.resolve("apache-maven-$version").absolutePath }
    }
    // Maven 4 needs JDK 17; the older lines run on it too.
    val javaHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
        .map { it.metadata.installationPath.asFile.absolutePath }
    val work = layout.buildDirectory.dir("maven-it-work")
    doFirst { work.get().asFile.deleteRecursively() }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dairgap.it.mavens=${homes.joinToString(",") { it.get() }}",
            "-Dairgap.it.javaHome=${javaHome.get()}",
            "-Dairgap.it.extensionPath=${extensionPath.files.joinToString(File.pathSeparator) { it.absolutePath }}",
            "-Dairgap.it.samples=${samples.asFile.absolutePath}",
            "-Dairgap.it.work=${work.get().asFile.absolutePath}",
            "-Dairgap.it.localRepo=${repo.get().asFile.absolutePath}",
            "-Dairgap.it.version=$airgapVersion",
        )
    })
}
tasks.check {
    dependsOn(integrationTestTask)
}
