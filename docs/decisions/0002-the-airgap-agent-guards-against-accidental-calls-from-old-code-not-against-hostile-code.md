---
status: accepted
decision-makers: Airgap maintainers
group: Why it exists
---

# The airgap agent guards against accidental calls from old code, not against hostile code

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

Code running inside a JVM can, with enough effort, get past anything else in that JVM: through native code, or tricks
with the JDK's internals. So an agent can't promise what real network separation promises. What it must cover depends
on what it's protecting against.

## Considered Options

* **Accidental calls from old business code:** code that uses the JDK's ordinary APIs, directly or through libraries,
  and reaches production because its configuration says so
  Matches the real risk in an organisation's own old code; an agent can cover it fully
* **Hostile code as well:** code that tries to get past the guard
  Would need true separation; impossible on the machines in question

## Decision Outcome

Chosen: accidental calls only. The README and design say so plainly. For anything more, real separation is needed:
a secrets scan before code runs at all, and a first run on CI runners that can't reach production.

### Consequences

* Good: a clear, testable promise: every route through the JDK's ordinary APIs is caught.
* Good: no false comfort: nobody is told the agent stops determined code.
* Bad: code with its own native networking, or that deliberately goes round the JDK, isn't caught. The design lists
  these limits.

## More Information

* Builds on: [The airgap agent is built rather than adopted, because no maintained tool does the job](0001-the-airgap-agent-is-built-rather-than-adopted-because-no-maintained-tool-does-the-job.md)
* Later decisions that build on this: [Refused calls fail as an unplugged machine would, never with made-up success](0003-refused-calls-fail-as-an-unplugged-machine-would-never-with-made-up-success.md)
* Background: [JEP 486: Permanently Disable the Security Manager](https://openjdk.org/jeps/486)
