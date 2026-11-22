---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# Refused calls fail as an unplugged machine would, never with made-up success

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

When the agent refuses a call, the code that made it carries on somehow. What it sees decides what its tests show, and
those first runs often become approval tests, which pin down what the code does today.

## Considered Options

* **Throw what an unplugged machine would throw** (`UnknownHostException`, `ConnectException`, `NamingException` and so
  on), at once, with a message naming the airgap agent
  The code takes its real offline path; nothing invented enters the tests
* **Return a made-up success** (an empty response, a fake connection)
  More code runs, but the behaviour is invented, and approval tests would pin it
* **Stop the whole run at the first refused call**
  Safest, but one call hides all the others, so the report is never complete

## Decision Outcome

Chosen: fail as an unplugged machine would. Three modes cover the rest: `refuse` (the default), `report` (for CI runs
whose network is already blocked) and `off`.

### Consequences

* Good: tests behave as they would offline, and the report lists every call, not just the first.
* Good: an engineer recognises the failures: they are the JDK's own exceptions.
* Bad: some old code handles network failure badly (endless retries, swallowed exceptions); the report shows these, and
  they become work in their own right.

## More Information

* Builds on: [The airgap agent guards against accidental calls from old code, not against hostile code](0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md)
* Later decisions that build on this: none yet
* Background: [the design: how a call is refused](../design.md#how-a-call-is-refused)
