---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# The report is one line of JSON per call, with a schema, and feeds the connection list

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

The report is half the point: the list of everything a project's code tried to reach is the first draft of its
connection list, the list of everything it depends on. A person reads it, and a tool turns it into draft entries.

## Considered Options

* **`build/airgap/calls.jsonl`: one JSON object per call, checked against a published schema, plus a summary of each
  distinct target at the end of the run**
  Easy to append to from many JVMs at once; easy for tools to read; readable enough for people
* **A single JSON or HTML report written at the end**
  Nicer to read, but lost if a JVM is killed, and hard to write from several JVMs
* **Log lines only**
  Simplest, but every reader has to parse free text

## Decision Outcome

Chosen: one line of JSON per call, with [its schema](../../schema/airgap-report.schema.json), and a summary. For
Spring applications each line also names the setting, and the file and line, that supplied the address.

### Consequences

* Good: a tool can turn the summary into draft connection-list entries for a person to confirm.
* Good: a changed schema is a visible, versioned change.
* Bad: one more format, which tools reading it depend on.

## More Information

* Builds on: [Refused calls fail as an unplugged machine would, never with made-up success](0003-refused-calls-fail-as-an-unplugged-machine-would-never-with-made-up-success.md)
* Later decisions that build on this: none yet
* Background: [the design: the report](../design.md#the-report)
