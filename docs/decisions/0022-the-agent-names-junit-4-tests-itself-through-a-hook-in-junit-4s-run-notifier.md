---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# The agent names JUnit 4 tests itself, through a hook in JUnit 4's run notifier

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

Each line of the report names the test that made the call, and tools that read the report use that name to say
which test reached out. Under
[One agent on the command line does the refusing, and JUnit only reports and allows](0004-one-agent-on-the-command-line-does-the-refusing-and-junit-only-reports-and-allows.md),
the test is named by listeners in `airgap-junit`: one for the JUnit Platform, found through Java's service loader,
and one for JUnit 4 run by itself, which the Maven extension names in Surefire's `listener` property. JUnit 4 has
no service loader for listeners, so a runner has to be told about one. Surefire 2.19.1 ignores the property and runs
no custom listener at all: a run of Apache Commons Net under it named no test. Gradle's own JUnit 4 runner and an
IDE's have no such property either. Old projects are mostly on JUnit 3 and 4. How are their tests named, whatever
runs them?

## Considered Options

* **A hook in JUnit 4's `RunNotifier`**, which every JUnit 4 runner reports each test through: the agent rewrites
  `fireTestStarted` and `fireTestFinished` when the application loads JUnit 4, and reads the test's name from its
  `Description` as text (`method(class)`), as JUnit 4 itself does, so the agent needs no JUnit.
  Good: works under every runner (every Surefire, Gradle's, the Vintage engine, an IDE's), with nothing for the build
  to set up. Bad: the agent rewrites a class that isn't the JDK's, which it can't check at start, since many JVMs
  have no JUnit 4; it's proved by the agent's own tests on every JDK instead. `@AllowCalls` on JUnit 4 tests still
  needs the listener, since the agent can't read that annotation without `airgap-junit`.
* **Keep the listener only, and warn where it can't run**
  Good: no new hook. Bad: the tests under Surefire 2.19, Gradle's JUnit 4 runner and IDEs stay unnamed, which are
  the old projects the agent is for.
* **Name the test from the call's stack**, by the first frame in a test class
  Good: no hook at all. Bad: a guess: a call from a helper class, a shared base class, a thread pool or a set-up
  method names the wrong test or none.

## Decision Outcome

Chosen by the airgap maintainers in discussion: the hook. `fireTestStarted` starts the test's scope before JUnit
tells any listener, and `fireTestFinished` ends it after every listener has heard, so the listener's scope, with the
class's `@AllowCalls`, nests inside it. A test named by both, as on the Vintage engine, is named the same by each.
This changes the record above in one respect: the agent now names JUnit 4 tests as well as refusing calls;
`airgap-junit` still names JUnit Platform tests and carries `@AllowCalls`.

### Consequences

* Good: every JUnit 3 and 4 test is named, however it's run.
* Bad: the first hook outside the JDK, which the check at start can't cover.
* Bad: calls in a JUnit 4 class's `@BeforeClass` and `@AfterClass` still name no test, since JUnit 4 reports no
  start for them.

## More Information

* Builds on: [One agent on the command line does the refusing, and JUnit only reports and allows](0004-one-agent-on-the-command-line-does-the-refusing-and-junit-only-reports-and-allows.md)
* Builds on: [Maven builds get the agent through a Maven core extension, without editing the pom](0020-maven-builds-get-the-agent-through-a-maven-core-extension-without-editing-the-pom.md)
* Later decisions that build on this: none yet
* Background: [the design: knowing which test made a call](../design.md#knowing-which-test-made-a-call)
