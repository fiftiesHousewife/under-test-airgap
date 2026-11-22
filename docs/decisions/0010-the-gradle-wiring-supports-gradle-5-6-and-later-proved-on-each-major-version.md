---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# The Gradle wiring supports Gradle 5.6 and later, proved on each major version

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

The Gradle wiring is a plugin that adds the agent to every `Test` and `JavaExec` task and to Spring Boot's `bootRun`,
so a project adopts the agent in one line. It runs inside the old project's own build, so it must work on the Gradle
version that project already uses: an old service can't be moved to a new Gradle before it can be run safely, because
moving it means running it. Old services are commonly on Gradle 4, 5 or 6, with Spring Boot 1.5 or 2.

## Considered Options

* **Gradle 4.10 and later**
  Reaches the oldest builds still common. `jvmArgumentProviders` exists (from Gradle 4.6), but several lazy APIs
  (`tasks.register`, `configureEach`, `Provider` mapping in places) are missing or incomplete, so the plugin needs
  more code paths. Gradle 4 runs only on JDK 8 to 10, so testing it needs JDK 8.
* **Gradle 5.6 and later**
  Covers most old projects still in use, including Spring Boot 2 builds. Every API the plugin needs (`configureEach`,
  `jvmArgumentProviders`, `withPlugin`, lazy `Provider`s) is there. Builds on Gradle 4 move to 5.6 first, which is a
  small step that doesn't need the code to run.
* **Gradle 6.9 and later**
  Simpler code still, but leaves out builds on Gradle 5, which are common among old services.
* **Gradle 7.6 and later**
  Modern APIs only; misses many of the projects the agent is for.

## Decision Outcome

Chosen in discussion: **Gradle 5.6 and later**. The plugin is written in Java, compiled for Java 8 like the agent,
and uses only the Gradle APIs that 5.6 has. Gradle TestKit runs a sample build under the plugin on the latest release
of Gradle 5, 6, 7, 8 and 9, each on a JDK it supports, and checks every guarded task starts with the agent.

### Consequences

* Good: one line adopts the agent on almost every old Gradle build, without first moving it to a new Gradle.
* Good: each supported major version is proved by a test, so a change that uses a newer API fails the build.
* Bad: the plugin can't use APIs newer than Gradle 5.6, such as the configuration cache's newer service APIs, without
  checking for them first.
* Bad: builds on Gradle 4 need moving to 5.6 before adopting the plugin; until then they can use
  [the try-it init script](../../tools/try-it/airgap.init.gradle).
* The TestKit tests download each Gradle distribution once, into the Gradle user home.

## More Information

* Builds on: [It runs on JDK 8 and later, needing no administrator rights or containers](0005-it-runs-on-jdk-8-and-later-needing-no-administrator-rights-or-containers.md)
* Later decisions that build on this: none yet
* Background: [the design: where it runs](../design.md#where-it-runs)
