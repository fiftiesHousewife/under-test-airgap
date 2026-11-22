---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# Native libraries are refused unless allowed, except the JDK's and the build tool's own

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

Native code runs outside the JVM's rules, so a library loaded with `System.loadLibrary` or `System.load` can reach
the network without passing any of the agent's hooks (see
[The airgap agent guards against accidental calls from old code, not against hostile code](0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md)).
Common cases are ordinary, not hostile: Netty's epoll and kqueue transports make their own connections in native
code, and fall back to Java sockets when their library won't load.

Many loads are harmless, though. The JDK loads its own libraries all the time (on JDK 8, through `System.loadLibrary`
itself). Gradle and Maven load native libraries for themselves in the build JVM, for file watching and coloured
console output. And some tests need one: sqlite-jdbc, for example, loads its database engine as a native library.

## Considered Options

* **Refuse, allowing the JDK's and the build tool's own**
  A load asked for by a JDK class (in the JDK's `java.`, `javax.`, `jdk.`, `sun.` or `com.sun.` packages) goes ahead and
  isn't reported.
  So does one asked for by Gradle (`org.gradle.`, and its native-platform library `net.rubygrapefruit.`) or Maven
  (`org.apache.maven.`, and in the build JVM the console library `org.fusesource.jansi.`). Any other load is refused
  with `UnsatisfiedLinkError`, as a missing library would fail, and reported, unless the settings allow it with
  `native:<name>`, such as `native:sqlitejdbc`.
* **Refuse everywhere but the JDK**
  Simpler, but the build JVM loses Gradle's file watching and Maven's coloured output unless each of their libraries
  is allowed by hand.
* **Report only**
  Nothing breaks, but code with native networking gets past the agent unseen except for one report line.

## Decision Outcome

Chosen in discussion: **refuse, allowing the JDK's and the build tool's own**. `System.loadLibrary`, `System.load`
and their `Runtime` versions are hooked, at their start: before the native-access check JDK 24 added, so the
self-check's probes don't set off its warning. On JDK 9 and later the JDK loads its own libraries another way; on
JDK 8 it uses these methods, and the class that called them shows it's the JDK's. The report says `kind: native-library`, with the library's name or path as its target. An allowlist entry
`native:<name>` matches a library by the name given to `loadLibrary`, or by its file name with the `lib` prefix and
extension taken off (`native:sqlitejdbc` matches `libsqlitejdbc.so` and `sqlitejdbc.dll`).

### Consequences

* Good: native networking can't go round the agent unseen; Netty falls back to Java sockets, which the agent sees.
* Good: the JDK and the build tools work as before, and the report isn't filled with their loads.
* Bad: a test that needs a native library fails until the library is allowed, with a report line naming it.
* Bad: a library that unpacks itself under a changing file name can only be allowed by the name it gives
  `loadLibrary`, or not at all if it uses `load` with a generated name.
* The class asking is the one that called `System.loadLibrary` or `System.load`; a library loaded through a helper
  in another package is judged by that helper.

## More Information

* Builds on: [Refused calls fail as an unplugged machine would, never with made-up success](0003-refused-calls-fail-as-an-unplugged-machine-would-never-with-made-up-success.md)
* Later decisions that build on this: none yet
* Background: [the design: what counts as a risky call](../design.md#what-counts-as-a-risky-call-and-where-its-caught)
