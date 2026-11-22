---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# An exit is refused only while a test is running

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

Old code sometimes ends the JVM itself, with `System.exit` or `Runtime.halt`: a batch job when it finishes, a
command-line tool on bad input, a service that can't reach its database. Inside a test run that ends the whole run
partway, often with no failed test to show why. The design refuses these calls in a test run with a
`SecurityException` naming the agent, so the test that made the call fails and the run carries on.

But test runners end their own JVM the same way once the tests finish: Gradle's test worker and Maven Surefire both
call `System.exit`. Refusing every exit in a test run would break every run. The agent needs a way to tell the
project's exit from the runner's.

## Considered Options

* **Only while a test is running**
  The JUnit listener tells the agent which test is running on each thread (see
  [One agent on the command line does the refusing, and JUnit only reports and allows](0004-one-agent-on-the-command-line-does-the-refusing-and-junit-only-reports-and-allows.md)).
  An exit on a thread running a test is the project's, so it's refused and reported. An exit with no test running,
  such as the runner's own at the end, goes ahead and isn't reported. The Gradle plugin always adds the listener.
  Without the listener (the agent added by hand, with no `airgap-junit`), nothing is known about tests, so no exit is
  refused. Work a test hands to a shared thread pool loses the link to the test, so an exit there isn't refused
  either.
* **When the project's code is on the stack**
  Refuses an exit made from the project's own packages, judged by the `project` setting, even outside a test and
  without the listener. Depends on that setting being right: without it, the agent judges the project's code as
  anything not the JDK, a test runner or a build tool, and a runner it doesn't know would be taken for the project.
* **Report only, never refuse**
  Safe for every runner, but a test that calls `System.exit` still ends the run partway, which is the problem.

## Decision Outcome

Chosen in discussion: **only while a test is running**. `Runtime.exit` (which `System.exit` calls) and `Runtime.halt`
are hooked. With a test running on the thread, the call is refused with a `SecurityException` and reported as
`kind: exit`, with the status as its target (`exit(1)`, `halt(1)`); in report mode it's reported and goes ahead.
With no test running, the hook does nothing.

### Consequences

* Good: a test that ends the JVM fails on its own, and the rest of the run carries on and is reported.
* Good: test runners, build tools and application starts end their JVMs as before.
* Bad: without `airgap-junit` on the tests' class path, exits aren't refused. The Gradle plugin adds it; a build that
  adds the agent by hand needs to add it too.
* Bad: an exit from a thread the test didn't start (a shared pool) isn't refused.
* The self-check can't call `exit` to prove the hook, since a missing hook would end the JVM. It proves the method
  is there to rewrite; the native-library probe proves the same class, `java.lang.Runtime`, is rewritten.

## More Information

* Builds on: [Refused calls fail as an unplugged machine would, never with made-up success](0003-refused-calls-fail-as-an-unplugged-machine-would-never-with-made-up-success.md)
* Builds on: [Every hook is proved on every supported JDK, and the agent checks its own hooks at start](0008-every-hook-is-proved-on-every-supported-jdk-and-the-agent-checks-its-own-hooks-at-start.md)
* Later decisions that build on this: none yet
* Background: [the design: what counts as a risky call](../design.md#what-counts-as-a-risky-call-and-where-its-caught)
