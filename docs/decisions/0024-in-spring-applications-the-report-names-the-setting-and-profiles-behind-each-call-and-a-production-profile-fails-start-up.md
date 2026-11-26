---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# In Spring applications, the report names the setting and profiles behind each call, and a production profile fails start-up

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

In old Spring applications the addresses a test reaches usually come from configuration, and the default profile is
often production: real hosts and passwords in a plain `application.properties`. A refused call's caller is then
Spring's or a driver's code, which says little about what to change. Configuration fetched at start-up (Spring Cloud
Config, Vault, a JNDI data source) is refused before the application has started, and the code never names it. What
should the agent do for Spring applications, and what stops a run under production settings?

## Considered Options

* **Hooks in Spring's own classes, read by reflection, as the JUnit 4 hook is:** the agent keeps each Spring
  `Environment` as it's made; at a call, it finds the property whose value holds the call's address, with Spring
  Boot's record of where it was read (file and line), and the active profiles; a context refreshed under a
  production profile fails to start; calls made while Spring is starting are marked.
  Good: the engineer sees which property each test profile must override, and a test run can't quietly use
  production settings. Bad: more hooks outside the JDK, proved by the agent's own tests with Spring, not by the check
  at start; the property is found by its value, so a value used by two properties names the first.
* **Report the profiles only**
  Good: small. Bad: the engineer still has to find each property by hand, the slowest part of writing a test profile.
* **End the JVM under a production profile**
  Good: nothing runs. Bad: other tests in the JVM are lost, and the run looks killed rather than refused.

## Decision Outcome

Chosen by the airgap maintainers in discussion: the hooks. Each report line from a Spring application carries
`setting` (the property's name, and `from`, where Spring Boot knows it) and `profiles` (the active profiles). A
context refreshed with an active profile named `prod`, `production` or `live` fails to start, with a message naming
the profile; a project names its own list in `testing/airgap.yaml` (`production_profiles`), and an empty list turns
the stop off. A call made while Spring is starting (`SpringApplication.run` or a context's refresh) is marked in the
report, and the summary groups such calls. Spring without Spring Boot gets the profiles and the setting's name,
without its file and line.

### Consequences

* Good: one override per refused address, read straight off the report, until a run's report is clean.
* Good: a test run under production settings fails at start, before it reaches anything.
* Bad: hooks in Spring's classes, which the check at start can't cover, and tests with Spring on every JDK.

## More Information

* Builds on: [The agent names JUnit 4 tests itself, through a hook in JUnit 4's run notifier](0022-the-agent-names-junit-4-tests-itself-through-a-hook-in-junit-4s-run-notifier.md)
* Later decisions that build on this: none yet
* Background: [the design: Spring applications](../design.md#spring-applications)
