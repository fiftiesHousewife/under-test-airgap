package io.github.fiftieshousewife.airgap.build

import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions

/**
 * Starts a task's JVM with the airgap agent this build makes, as the Gradle plugin would: the jar on the bootstrap
 * class path and named as the agent, its report cleared when the task starts. The tests also get the report's path
 * and the jar's, as the system properties `airgap.test.report` and `airgap.test.agent`.
 *
 * @param agent the agent's jar
 * @param report where the agent writes its report
 * @param settings the agent's settings besides the report, such as `run=test` or `allow=...`
 */
fun <T> T.runWithOwnAgent(agent: FileCollection, report: Provider<RegularFile>, settings: String)
        where T : Task, T : JavaForkOptions {
    inputs.files(agent).withPropertyName("airgapAgent")
    doFirst { report.get().asFile.delete() }
    jvmArgumentProviders.add(OwnAgentArguments(agent, report, settings))
}

private class OwnAgentArguments(
    private val agent: FileCollection,
    private val report: Provider<RegularFile>,
    private val settings: String,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        val jar = agent.singleFile.absolutePath
        val file = report.get().asFile.absolutePath
        return listOf(
            "-Xbootclasspath/a:$jar",
            "-javaagent:$jar=report=$file;$settings",
            "-Dairgap.test.report=$file",
            "-Dairgap.test.agent=$jar",
        )
    }
}
