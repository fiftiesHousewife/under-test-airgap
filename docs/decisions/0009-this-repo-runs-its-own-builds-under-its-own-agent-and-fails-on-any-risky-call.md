---
status: accepted
decision-makers: Airgap maintainers
group: How this repo works
---

# This repo runs its own builds under its own agent, and fails on any risky call

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

The agent's own tests prove each hook on purpose-made calls. Running real code under the agent, every build, shows it
works on code that wasn't written to test it, and that this repo's own code makes no risky calls.

## Considered Options

* **Attach the agent this build makes to every test run and tool run except the agent's own unit tests, and fail the
  build if any of them makes a risky call**
  The repo uses what it makes, every build; CI needs nothing it doesn't already build
* **Attach the published agent, from Maven Local or a repository**
  Tests a release, but CI has no Maven Local, and the build would depend on an earlier release of itself
* **Don't apply it**
  Simplest, but the agent never meets code it wasn't written for

## Decision Outcome

Chosen: the first option. The JUnit part's tests, the repo checks' tests and the repo checks themselves run with the
agent attached, and fail if any of them makes a risky call. The agent's own unit tests run without it: with it
attached, they would test the jar's copy of its classes rather than the code being built.

### Consequences

* Good: every build runs real code under the agent, on the agent's newest version.
* Good: a risky call added to this repo's own code fails its build.
* Bad: a tool here that needs the network one day must say so, with an allowlist entry and a reason.

## More Information

* Builds on: [The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues](0007-the-code-is-held-to-strict-quality-checks-run-by-the-build-and-pushes-go-through-a-script-that-syncs-the-issues.md)
* Later decisions that build on this: none yet
* Background: [the design: testing it](../design.md#testing-it)
