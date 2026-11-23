---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# Test JVMs let agents attach to themselves, so Mockito starts no java program

**Decided by:** Airgap maintainers · **Status:** accepted

## Context and Problem Statement

Mockito's inline mock maker (the default from Mockito 5) attaches an agent to the test JVM through Byte Buddy. On JDK 9
and later a JVM may not attach to itself unless started with `-Djdk.attach.allowAttachSelf=true`, so Byte Buddy
starts a second `java` program to attach from outside. The airgap agent refuses that program, as it refuses any program
not allowed, so Mockito can't start: on Spring PetClinic, run under the agent, 50 of 81 tests failed. This repo's own
test of Mockito passed only because its JVM allowed `process:java` for other tests.

## Considered Options

* **Let test JVMs attach to themselves**
  The Gradle plugin, the try-it script and this repo's own wiring add `-Djdk.attach.allowAttachSelf=true` to every test
  JVM they start with the agent. Byte Buddy then attaches from inside the JVM, through the JDK's own attach module and
  a local socket: no program starts, and nothing needs allowing. Mockito's documentation suggests the same setting.
  Good: works for Mockito and anything else built on Byte Buddy's agent, with no rule in the agent.
  Bad: whoever names the agent by hand must add the setting too; the README says so. Applications started under the
  agent don't get it, since they rarely attach agents to themselves.
* **The agent recognises Byte Buddy's attacher**
  The running JDK's own `java`, started with Byte Buddy's attacher pointed back at the same JVM, is allowed.
  Good: works however the agent is named.
  Bad: a special case tied to one library's command line, which can change.
* **Projects allow `process:java`**
  Bad: tests could then start any JVM, and it would run without the agent.

## Decision Outcome

Chosen by the airgap maintainers in discussion: let test JVMs attach to themselves. `AgentArguments` in the plugin
adds the setting for test tasks, as do `tools/try-it/airgap.init.gradle` and `OwnAgent.kt`. `OtherAgentsTest` runs in
its own task, `otherAgentsOnJdk<version>`, with an agent that allows nothing, and checks that Mockito works and that no
`java` program was started.

### Consequences

* Good: Mockito's inline mock maker works under the agent on every supported JDK, as projects run it.
* Bad: one more line for anyone who wires the agent by hand.

## More Information

* Builds on: [A native library whose file is in the JDK's own library folder is the JDK's own, whoever asks for it](0017-a-native-library-whose-file-is-in-the-jdks-own-library-folder-is-the-jdks-own-whoever-asks-for-it.md)
* Later decisions that build on this: none yet
* Background: [the design: living alongside other agents](../design.md#living-alongside-other-agents)
