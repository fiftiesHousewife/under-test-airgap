---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# One agent on the command line does the refusing, and JUnit only reports and allows

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

Risky calls happen in test runs, in application starts and in the build itself. A JUnit extension knows which test is
running, but can only reach the JDK through weak hooks (a socket factory, the HTTP proxy settings, the removed
Security Manager), none of which catches NIO channels, and it never sees the application or the build.

## Considered Options

* **A Java agent added with `-javaagent` refuses; a JUnit Platform listener and extension only report which test is
  running and let a test allow a stand-in**
  One way of refusing, the same on every JDK, in every kind of run
* **A JUnit 6 extension alone**
  Simplest to adopt, but tests only, weak hooks, and JUnit 6 needs Java 17
* **The JUnit extension loads the agent itself when tests start, as Mockito does**
  No command-line change, but JDK 21 and later warn about agents loaded this way and plan to refuse them by default

## Decision Outcome

Chosen: the agent on the command line refuses; JUnit only reports and allows. The listener and extension are built
against the JUnit 5 API, so they run on JUnit 5 (JDK 8 and later) and JUnit 6 (JDK 17 and later). The build wiring for
Gradle and Maven adds the agent; nobody adds it by hand.

### Consequences

* Good: the same protection in tests, application starts and the build.
* Good: the report names the test behind each call.
* Bad: every JVM must be started with the agent, so the wiring has to find them all (forked test JVMs, PIT's, the Gradle
  daemon).
* Bad: repos still on JUnit 4 move onto the JUnit Platform first (their tests run unchanged on its Vintage engine).

## More Information

* Builds on: [The airgap agent is built rather than adopted, because no maintained tool does the job](0001-the-airgap-agent-is-built-rather-than-adopted-because-no-maintained-tool-does-the-job.md)
* Later decisions that build on this: [It runs on JDK 8 and later, needing no administrator rights or containers](0005-it-runs-on-jdk-8-and-later-needing-no-administrator-rights-or-containers.md)
* Background: [JEP 451: Prepare to Disallow the Dynamic Loading of Agents](https://openjdk.org/jeps/451)
