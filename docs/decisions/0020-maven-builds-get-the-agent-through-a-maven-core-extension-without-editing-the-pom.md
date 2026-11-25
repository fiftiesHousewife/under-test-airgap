---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# Maven builds get the agent through a Maven core extension, without editing the pom

**Decided by:** Airgap maintainers · **Status:** accepted

## Context and Problem Statement

Many old Java services build with Maven. Today a Maven project can use the agent only if someone edits its
`pom.xml`, and the person testing an old service often may not, or shouldn't have to. Maven starts the project's
code in four places:

* **unit tests:** Surefire, in a JVM it forks;
* **integration tests:** Failsafe, the same way;
* **starting the application:** `spring-boot:run` or `exec:exec` in a forked JVM, or `exec:java` inside Maven's own;
* **Maven itself:** its own JVM, which runs the plugins and any build code.

Each needs the agent, its report in the module's `target` folder, the build's root folder, the project's
`testing/airgap.yaml`, and, for tests, the JUnit listener that names each test. How does Maven get all that without a
change to the pom?

## Considered Options

* **A Maven core extension.** Maven loads it before it reads any pom, from a jar named with
  `-Dmaven.ext.class.path=...` on the command line or in `MAVEN_ARGS` (Maven 3.9 and later), or from
  `.mvn/extensions.xml` in the project. Once Maven has read the poms, the extension changes each module's Surefire,
  Failsafe and run plugins in memory, adding the agent to the JVM arguments and the JUnit listener to the tests' class
  path. It keeps whatever `argLine` the module already sets, such as JaCoCo's. Nothing on disk changes.
  Good: works for every module, whatever its pom says, and needs no rights beyond running Maven. It's the Maven
  equivalent of the Gradle try-it script, and with `.mvn/extensions.xml` it's the equivalent of the plugin.
  Bad: a new artifact to build, publish and prove on several Maven versions. It relies on Maven's lifecycle
  participant API, which is stable from Maven 3.0 to 4.
* **A profile in the person's own `~/.m2/settings.xml`, setting the `argLine` property.**
  Good: no new code.
  Bad: a profile can only set properties, and any module that writes its own `<argLine>` in the pom ignores the
  property. That's common, since it's how JaCoCo is wired, so the agent would be missing from the builds most likely
  to need it, without any sign. It can't add the JUnit listener, or a report folder per module.
* **A Maven plugin the project adds to its pom.**
  Good: the usual way to extend a Maven build.
  Bad: it needs the pom edited, which is what this has to avoid. It could come later, for teams that adopt the
  agent for good.
* **`JAVA_TOOL_OPTIONS=-javaagent:...`**, which every JVM started from that shell picks up.
  Good: nothing to install.
  Bad: Maven, Surefire's forks and any other Java program get the same settings, with one report for all and no way
  to tell tests from the build. It names no tests, and each JVM prints a notice to standard error, which some tests
  check. A fallback to mention, not the way in.

## Decision Outcome

Chosen by the airgap maintainers in discussion: a Maven core extension,
`io.github.fiftieshousewife.airgap:airgap-maven-extension`:

* **What it changes:**
  * Surefire's and Failsafe's `argLine`: the agent is added to whatever the module already sets, with
    `run=test` and a report at `target/airgap/<plugin>-<execution>/calls.jsonl`;
  * the tests' class path: the JUnit listener;
  * the run plugins' JVM arguments: `spring-boot:run` (`jvmArguments`) and `exec:exec`, with `run=application`.
* **What it passes:** each module's `testing/airgap.yaml`, the build's root folder (Maven's top-level project folder)
  as `root`, and `-Djdk.attach.allowAttachSelf=true` for tests.
* **How it carries the agent:** the extension depends on `airgap-agent` and `airgap-junit` as ordinary artifacts, so
  each shows in a security review's bill of materials. Named in `.mvn/extensions.xml`, Maven fetches all three; for
  a one-off run, a small try-it script fetches them and names the three jars in `-Dmaven.ext.class.path`. One jar
  with the others inside it was turned down: jars hidden in a jar are harder for scanners and reviewers to see.
* **What it leaves alone:** code Maven runs in its own JVM: Surefire with `forkCount=0`, and `exec:java`. The extension
  warns about each. For these, the README shows how to run Maven itself under the agent with `MAVEN_OPTS` or
  `.mvn/jvm.config`, as it does for Gradle.
* **Testing:** it is proved by sample builds on the latest Maven 3.6, 3.8, 3.9 and 4, run as the Gradle plugin's are
  on each Gradle. A sample that sets its own `argLine`, one that uses JaCoCo, and a multi-module one are among them.
* **Real projects:** it is tried on real open-source Maven projects before release.

### Consequences

* Good: a Maven project is tested under the agent with one command-line setting, and no change to its files.
* Good: a team that keeps the agent adds one `.mvn/extensions.xml`, and its whole build is covered.
* Bad: a third published artifact, with its own tests on several Maven versions.
* Bad: code Maven runs in its own JVM needs the agent on Maven itself, which the extension can't add.

## More Information

* Builds on: [Refused calls fail as an unplugged machine would, never with made-up success](0003-refused-calls-fail-as-an-unplugged-machine-would-never-with-made-up-success.md)
* Builds on: [Test JVMs let agents attach to themselves, so Mockito starts no java program](0018-test-jvms-let-agents-attach-to-themselves-so-mockito-starts-no-java-program.md)
* Later decisions that build on this: [The agent names JUnit 4 tests itself, through a hook in JUnit 4's run notifier](0022-the-agent-names-junit-4-tests-itself-through-a-hook-in-junit-4s-run-notifier.md)
* Background: [the design: where it runs](../design.md#where-it-runs)
