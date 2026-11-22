// Conventions for every Java project. The standards they enforce are listed in AGENTS.md: Checkstyle, PMD (complexity,
// the assertAll rule), copy-paste detection, SpotBugs with FindSecBugs, forbidden APIs and line coverage, with the
// rule files in config/quality. Out-of-date dependencies are reported by `./gradlew dependencyUpdates` (Ben Manes
// versions plugin), and checked in CI. Mutation testing is set up in the agent's own build, where it matters.

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import groovy.json.JsonSlurper
import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis

plugins {
    java
    jacoco
    checkstyle
    pmd
    id("com.github.spotbugs")
    id("de.thetaphi.forbiddenapis")
    id("io.github.ben-manes.versions")
}

group = "io.github.fiftieshousewife.airgap"

val libs = the<VersionCatalogsExtension>().named("libs")
fun version(alias: String) = libs.findVersion(alias).get().requiredVersion
fun library(alias: String) = libs.findLibrary(alias).get()
val qualityConfig = rootProject.layout.projectDirectory.dir("config/quality")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(version("java"))
    }
}

dependencies {
    // Lombok, for builders and, where a project logs, @Slf4j. Projects that log add SLF4J themselves.
    compileOnly(library("lombok"))
    annotationProcessor(library("lombok"))
    testCompileOnly(library("lombok"))
    testAnnotationProcessor(library("lombok"))

    testImplementation(platform(library("junit-bom")))
    testImplementation(library("junit-jupiter"))
    testImplementation(library("assertj-core"))
    testRuntimeOnly(library("junit-platform-launcher"))

    spotbugsPlugins(library("findsecbugs"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror", "-parameters"))
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// ---------- Tests and coverage ----------
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = version("jacoco")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = version("min-line-coverage").toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ---------- Code-quality standards ----------
checkstyle {
    toolVersion = version("checkstyle")
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    configFile = qualityConfig.file("checkstyle/checkstyle.xml").asFile
    configDirectory = qualityConfig.dir("checkstyle").asFile
}

pmd {
    toolVersion = version("pmd")
    ruleSets = emptyList()
    isConsoleOutput = true
    isIgnoreFailures = false
}

tasks.withType<Pmd>().configureEach {
    ruleSetFiles = files(qualityConfig.file("pmd/ruleset.xml"))
    // The JDK's own classes, from the toolchain, so PMD resolves types against Java 25 instead of guessing.
    val jdkClasses = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(version("java")) }
        .map { it.metadata.installationPath.file("lib/jrt-fs.jar") }
    classpath = (classpath ?: files()).plus(files(jdkClasses))
}

// ---------- Copy-paste detection: PMD's own CPD, run directly (no extra plugin to mirror) ----------
val cpdTool = configurations.create("cpdTool") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    cpdTool("net.sourceforge.pmd:pmd-cli:${version("pmd")}")
    cpdTool("net.sourceforge.pmd:pmd-java:${version("pmd")}")
}
val cpdCheck = tasks.register<JavaExec>("cpdCheck") {
    group = "verification"
    description = "Fails if 75 or more tokens of Java code are copy-pasted (PMD's CPD)."
    val sources = files("src/main/java", "src/test/java", "src/integrationTest/java").filter { it.isDirectory }
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    classpath = cpdTool
    mainClass = "net.sourceforge.pmd.cli.PmdCli"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(version("java")) }
    args("cpd", "--minimum-tokens", "75", "--language", "java", "--format", "text")
    argumentProviders.add(CommandLineArgumentProvider { sources.files.sorted().flatMap { listOf("--dir", it.path) } })
}
tasks.check {
    dependsOn(cpdCheck)
}

// ---------- Bugs and security bugs ----------
spotbugs {
    toolVersion = version("spotbugs")
    effort = Effort.MAX
    reportLevel = Confidence.LOW
    ignoreFailures = false
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
}

// ---------- Forbidden APIs ----------
forbiddenApis {
    bundledSignatures = setOf(
        "jdk-unsafe",
        "jdk-deprecated",
        "jdk-internal",
        "jdk-non-portable",
        "jdk-system-out",
        "jdk-reflection",
    )
    ignoreFailures = false
}

tasks.withType<CheckForbiddenApis>().configureEach {
    signaturesFiles = files(qualityConfig.file("forbidden-apis/signatures.txt"))
}

// ---------- Out-of-date dependencies ----------
fun isPreRelease(version: String) =
    Regex("(?i).*[.-](alpha|beta|rc|cr|m|milestone|preview|snapshot|ea)[.\\d-]*").matches(version)

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isPreRelease(candidate.version) && !isPreRelease(currentVersion) }
    outputFormatter = "json,plain"
    outputDir = layout.buildDirectory.dir("reports/dependencyUpdates").get().asFile.path
    reportfileName = "report"
}

// Reads the versions report. Warns about out-of-date dependencies, or fails when quality.failOnStaleDependencies=true.
val failOnStaleDependencies = providers.gradleProperty("quality.failOnStaleDependencies").map { it.toBoolean() }.orElse(false)
val dependencyReport = layout.buildDirectory.file("reports/dependencyUpdates/report.json")
val checkDependencyVersions = tasks.register("checkDependencyVersions") {
    group = "verification"
    description = "Reports out-of-date dependencies; fails if quality.failOnStaleDependencies is true."
    dependsOn(tasks.withType<DependencyUpdatesTask>())
    val report = dependencyReport
    val fail = failOnStaleDependencies
    doLast {
        @Suppress("UNCHECKED_CAST")
        val outdated = (JsonSlurper().parse(report.get().asFile) as Map<String, Any>)["outdated"] as Map<String, Any>
        val count = (outdated["count"] as Number).toInt()
        if (count > 0) {
            val message = "$count out-of-date dependencies; see ${report.get().asFile.parentFile}/report.txt"
            if (fail.get()) throw GradleException(message) else logger.warn(message)
        }
    }
}

if (providers.environmentVariable("CI").isPresent) {
    tasks.check {
        dependsOn(checkDependencyVersions)
    }
}
