---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# A native library whose file is in the JDK's own library folder is the JDK's own, whoever asks for it

**Decided by:** Airgap maintainers · **Status:** accepted

## Context and Problem Statement

[A native library is the JDK's own only when a JDK class loader can see the class asking for it](0013-a-native-library-is-the-jdks-own-only-when-a-jdk-class-loader-can-see-the-class-asking-for-it.md)
judges a load by the class asking for it. On JDK 8, the JDK's attach API is in `tools.jar`, which no JDK class loader
loads: Byte Buddy's agent loads it in a class loader of its own when Mockito's inline mock maker attaches itself. Its
`sun.tools.attach` classes then load the JDK's own `attach` library, and the agent refused it, so Mockito's inline mock
maker couldn't start on JDK 8 under the agent. JDK 9 and later aren't affected: the attach API is a JDK module there.

## Considered Options

* **Judge by file as well as by class**
  A library also counts as the JDK's own when its file is in the running JDK's own library folder
  (`sun.boot.library.path`): a name that folder has, which `System.loadLibrary` and `Runtime.loadLibrary` look for there
  first, or a path to a file in it. The foreign function API looks names up through the operating system, not that
  folder, so for it only a path counts.
  Good: fixes Mockito on JDK 8, and any other JDK library loaded by JDK code in another class loader, with nothing for
  users to set. A project can't put files in the JDK's folder, so the routes 0013 closed stay closed: JNA's library and
  a project's own libraries are still judged as the project's.
  Bad: a class loader that overrides `findLibrary` to hand back a file of its own for a name the JDK's folder also has
  would pass. That is a deliberate trick, outside what the agent guards against.
* **Users allow it**
  No rule change; projects on JDK 8 that use Mockito's inline mock maker add `native:attach`.
  Bad: every such project fails first, and has to find out why.
* **The plugin allows it**
  The Gradle plugin adds `native:attach` itself.
  Bad: covers plugin users only, and allows `attach` from any caller, not only the JDK's.

## Decision Outcome

Chosen by the airgap maintainers in discussion: judge by file as well as by class. `LibraryLoads.isJdksFile` holds the
rule and `NativeGate` applies it; `LibraryLoadsTest` proves it, and `OtherAgentsTest` proves Mockito's inline mock
maker runs under the agent on every supported JDK.

### Consequences

* Good: Mockito's inline mock maker works on JDK 8 under the agent, as on every later JDK.
* Good: 0013 stands; this adds a second way a library can be the JDK's own, and takes nothing away.
* Bad: one more check on each native library load: whether a file exists in the JDK's folder.

## More Information

* Builds on: [A native library is the JDK's own only when a JDK class loader can see the class asking for it](0013-a-native-library-is-the-jdks-own-only-when-a-jdk-class-loader-can-see-the-class-asking-for-it.md)
* Later decisions that build on this: none yet
* Background: [the design: living alongside other agents](../design.md#living-alongside-other-agents)
