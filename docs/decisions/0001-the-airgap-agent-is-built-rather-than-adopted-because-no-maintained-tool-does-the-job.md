---
status: accepted
decision-makers: Airgap maintainers
group: Why it exists
---

# The airgap agent is built rather than adopted, because no maintained tool does the job

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

Old code has to run, the first time, without reaching production, on engineers' own machines: Windows laptops, no
administrator rights, no container runtime, nothing installed from the internet, and projects on JDK 8 and later. A
list of every call the code tried is needed too, as the start of a list of everything it depends on. Existing tools
come first; every option found, and why each fails on corporate machines, is in
[the design's options considered](../design.md#options-considered).

## Considered Options

* **Build a small Java agent on Byte Buddy, using the approach Kestra uses in place of the Security Manager**
  Works within every restriction; we maintain it; Byte Buddy is already approved in most companies because Mockito
  depends on it
* **Adopt junit-airgap**
  Closest in purpose, but a single-author beta, tests only, with a native library to approve on each operating system
* **Adopt Sniffy**
  Refuses sockets, but only as a JUnit 4 rule, with no sign of recent work
* **Adapt a security agent (Jazzer's sanitizers, OpenRASP)**
  Proven hooks, but built to find attacks; heavy, and OpenRASP is no longer released
* **Use operating-system sandboxes or containers**
  The real separation the JDK recommends, but impossible on Windows without administrator rights, and no
  containers on laptops
* **The Java Security Manager**
  Refuses by policy, but is disabled from JDK 24
* **Network separation in CI only**
  Real separation, but no help on an engineer's own machine

## Decision Outcome

Chosen: build our own, on Byte Buddy, in this repo. The tools above are where its list of calls to catch comes from,
and they're credited in the design.

### Consequences

* Good: it fits every restriction, and does exactly what's needed: refuse, and report with the caller.
* Good: the JVM gains a maintained "no network" tool like Ruby's WebMock or Python's pytest-socket.
* Bad: we maintain hooks into JDK internals that change between versions, tested on every supported JDK.
* Bad: one more artifact for each organisation to review and mirror.

## More Information

* Builds on: nothing (a foundation decision)
* Later decisions that build on this: [The airgap agent guards against accidental calls from old code, not against hostile code](0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md), [One agent on the command line does the refusing, and JUnit only reports and allows](0004-one-agent-on-the-command-line-does-the-refusing-and-junit-only-reports-and-allows.md)
* Background: [the design's options considered, with references](../design.md#options-considered)
