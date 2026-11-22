---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# It runs on JDK 8 and later, needing no administrator rights or containers

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

The repos it guards build on JDK 8, 11, 17 and later. The machines they run on are mostly Windows, with no
administrator rights and no container runtime, and every tool needs a security review before it can be used.

## Considered Options

* **Java 8 bytecode; one jar with Byte Buddy copied inside under the agent's own package name; tested on JDK 8, 11, 17,
  21 and 25**
  Runs wherever the repo does; no clash with the Byte Buddy that Mockito brings
* **Build for the newest JDK only**
  Simpler code, but useless on the repos that need it most
* **A native agent (JVMTI)**
  Sees more, but needs a library per operating system, and a heavier review

## Decision Outcome

Chosen: Java 8 bytecode, one self-contained jar, tested on every supported JDK. Its build uses JDK 25 to compile (with
`--release 8`) and Gradle's toolchains to run the tests on each older JDK.

### Consequences

* Good: one artifact to approve, with one dependency (Byte Buddy, Apache-2.0) inside it.
* Good: works for a plain Windows user.
* Bad: the agent's own code can't use anything newer than Java 8; the JUnit parts build against the JUnit 5 API.
* Bad: the build needs five JDKs, which CI provides through toolchains.

## More Information

* Builds on: [One agent on the command line does the refusing, and JUnit only reports and allows](0004-one-agent-on-the-command-line-does-the-refusing-and-junit-only-reports-and-allows.md)
* Later decisions that build on this: none yet
* Background: [the design: the problem](../design.md#the-problem)
