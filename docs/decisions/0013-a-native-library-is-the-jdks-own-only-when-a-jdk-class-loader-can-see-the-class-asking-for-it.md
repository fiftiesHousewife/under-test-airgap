---
status: proposed
decision-makers: Airgap maintainers
group: How it works
---

# A native library is the JDK's own only when a JDK class loader can see the class asking for it

**Decided by:** Airgap maintainers · **Status:** proposed

## Context and Problem Statement

[Native libraries are refused unless allowed, except the JDK's and the build tool's own](0012-native-libraries-are-refused-unless-allowed-except-the-jdks-and-the-build-tools-own.md)
lets a load go ahead when a JDK class asks for it, and recognises the JDK's classes by package: `java.`, `javax.`,
`jdk.`, `sun.` and `com.sun.`. An independent review showed two ways round that, both proved with the agent's jar on
JDK 21:

* Libraries on the class path use those packages too. JNA, which Testcontainers and many desktop libraries use, is
  `com.sun.jna`, so its native code loaded unchecked.
* A load made through reflection (`Method.invoke` on `System.loadLibrary`) was judged by the reflection classes, which
  are the JDK's, so it went ahead too.

## Considered Options

* **By class loader, skipping reflection**
  A class is the JDK's when the class loader just above the class path can see it: the platform class loader on JDK 9
  and later, the extension class loader on JDK 8, and through them the bootstrap loader. That loader can't see the class
  path, so no project class passes, whatever its package. The class asking is the first one after `System` and
  `Runtime` that isn't reflection or a method handle (`java.lang.reflect.`, `jdk.internal.reflect.`, `sun.reflect.`,
  `java.lang.invoke.`).
  Good: closes both routes; works the same on JDK 8 and every later JDK.
  Bad: on JDK 8, a jar someone put in the JDK's extension folder counts as the JDK's.
* **By package, with JNA's package named as an exception**
  Good: no class loading while deciding.
  Bad: every other library in a JDK-like package is still a way round, and reflection still is.
* **By the class object on the stack** (`StackWalker` on JDK 9 and later)
  Good: exact.
  Bad: JDK 8 has no public equivalent, so JDK 8 would need a different rule, tested separately.

## Decision Outcome

Proposed: by class loader, skipping reflection. It is built and tested on JDK 11, 17, 21 and 25, with tests that load
a library from a `com.sun.` class on the class path and through reflection, both refused.

### Consequences

* Good: a library on the class path is judged as the project's, whatever its package and however it calls the load.
* Bad: a project using JNA must allow its library (`native:jnidispatch`) or see its tests fail, as for any other
  native library.
* `LibraryLoads` in the agent holds both rules, and `LibraryLoadsTest` and `RefusedCallsTest` prove them.

## More Information

* Builds on: [Native libraries are refused unless allowed, except the JDK's and the build tool's own](0012-native-libraries-are-refused-unless-allowed-except-the-jdks-and-the-build-tools-own.md)
* Later decisions that build on this: none yet
* Background: [the design: what counts as a risky call](../design.md#what-counts-as-a-risky-call-and-where-its-caught)
