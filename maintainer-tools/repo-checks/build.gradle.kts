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
