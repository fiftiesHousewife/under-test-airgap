// How every published part of the airgap agent is published: to Maven Central (through its Central Portal, by
// nmcp, from the root project), with sources, Javadoc, a CycloneDX software bill of materials and signatures; and to a
// local repository in the build folder that the Gradle plugin's integration tests read.
//
// Signing needs signingInMemoryKey and signingInMemoryKeyPassword (Gradle properties, or ORG_GRADLE_PROJECT_
// environment variables in CI). Without them, publications go unsigned, which only the local repository accepts.
plugins {
    java
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
    id("org.cyclonedx.bom")
}

java {
    withSourcesJar()
    withJavadocJar()
}

// Javadoc reads the sources after Lombok has written out what its annotations generate, so it sees every class.
val lombokTool = configurations.create("lombokTool") {
    isCanBeConsumed = false
}
val lombok = project.the<VersionCatalogsExtension>().named("libs").findLibrary("lombok").get()
dependencies {
    lombokTool(lombok)
}
val mainSources = sourceSets.main.get()
val delombok = tasks.register<JavaExec>("delombok") {
    description = "Writes the main sources out with Lombok's generated code, for Javadoc."
    val sources = mainSources.java.sourceDirectories.filter { it.isDirectory }
    val output = layout.buildDirectory.dir("delombok")
    val compileClasspath = mainSources.compileClasspath
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(compileClasspath).withNormalizer(ClasspathNormalizer::class.java)
    outputs.dir(output)
    classpath = lombokTool
    mainClass = "lombok.launch.Main"
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("delombok") + sources.files.map { it.path } +
            listOf("-d", output.get().asFile.path, "--classpath", compileClasspath.asPath)
    })
}
tasks.named<Javadoc>("javadoc") {
    dependsOn(delombok)
    setSource(layout.buildDirectory.dir("delombok"))
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

// The bill of materials lists what the published jar is built from: its runtime class path.
val bom = tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs = listOf("runtimeClasspath")
    componentName = project.the<BasePluginExtension>().archivesName
}
val bomFile = bom.flatMap { it.jsonOutput }

publishing {
    repositories {
        maven {
            name = "integration"
            url = uri(rootProject.layout.buildDirectory.dir("integration-repo"))
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            url = "https://github.com/fiftiesHousewife/under-test-airgap"
            licenses {
                license {
                    name = "Apache License 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0"
                }
            }
            developers {
                developer {
                    id = "fiftieshousewife"
                    name = "Airgap maintainers"
                    url = "https://github.com/fiftiesHousewife"
                }
            }
            scm {
                url = "https://github.com/fiftiesHousewife/under-test-airgap"
                connection = "scm:git:https://github.com/fiftiesHousewife/under-test-airgap.git"
                developerConnection = "scm:git:https://github.com/fiftiesHousewife/under-test-airgap.git"
            }
        }
    }
}

// Each jar's publication (not a plugin marker, which is a POM alone) carries its bill of materials.
publishing.publications.withType<MavenPublication>().matching { !it.name.endsWith("PluginMarkerMaven") }.configureEach {
    artifact(bomFile) {
        classifier = "cyclonedx"
        extension = "json"
        builtBy(bom)
    }
}

val signingKey = providers.gradleProperty("signingInMemoryKey")
signing {
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), providers.gradleProperty("signingInMemoryKeyPassword").orNull)
        sign(publishing.publications)
    }
}
