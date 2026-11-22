plugins {
    id("io.github.fiftieshousewife.java-conventions")
}

// Checks this repository's own files, and rebuilds the decision indexes. See AGENTS.md.
dependencies {
    implementation(libs.json.schema.validator)
    implementation(libs.jackson3.yaml)
}

val repoRoot = rootDir.absolutePath
val mainClassName = "io.github.fiftieshousewife.undertest.tools.repochecks.RepoChecks"

val checkRepoFiles = tasks.register<JavaExec>("checkRepoFiles") {
    group = "verification"
    description = "Fails on broken links, YAML files that don't match their schema, and out-of-date decision indexes."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    args("check", repoRoot)
}

tasks.register<JavaExec>("buildDecisionIndexes") {
    group = "documentation"
    description = "Rebuilds docs/decisions/README.md and decision-guides/README.md from the records."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    args("index", repoRoot)
}

tasks.register<JavaExec>("fixDecisionLinks") {
    group = "documentation"
    description = "Re-points links to decision records at their current files, after renumbering or moving them."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassName
    args("fix-links", repoRoot)
}

checkRepoFiles.configure {
    mustRunAfter("fixDecisionLinks", "buildDecisionIndexes")
}

tasks.check {
    dependsOn(checkRepoFiles)
}

// This repo applies its own agent: the repo checks' tests and the repo checks themselves run with the agent this
// build makes attached, and fail if they make any risky call at all. (The agent's own unit tests run without it, since
// with it attached they would test the jar's copy of its classes, not the code being built.)
val airgapAgent: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    airgapAgent(project(mapOf("path" to ":agent", "configuration" to "agentJarElements")))
}

fun Task.guardedByTheAgent(run: String) {
    val agent = files(airgapAgent)
    val report = layout.buildDirectory.file("airgap/$name.jsonl")
    inputs.files(agent)
    (this as JavaForkOptions).jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Xbootclasspath/a:${agent.singleFile.absolutePath}",
            "-javaagent:${agent.singleFile.absolutePath}=report=${report.get().asFile.absolutePath};run=$run",
        )
    })
    doFirst { report.get().asFile.delete() }
    doLast {
        if (report.get().asFile.exists()) {
            throw GradleException("$path made risky calls, which the airgap agent refused; see ${report.get().asFile}")
        }
    }
}

tasks.test {
    guardedByTheAgent("test")
}
checkRepoFiles.configure {
    guardedByTheAgent("application")
}
