---
status: accepted
decision-makers: Airgap maintainers
group: How this repo works
---

# CI stays on setup-gradle v5 for now, rather than accept v6's terms for caching

**Decided by:** Airgap maintainers · **Status:** accepted

## Context and Problem Statement

CI sets up Gradle with `gradle/actions/setup-gradle`. From v6, the action stays MIT-licensed, but its caching comes
under Gradle's proprietary terms of use. v5 still works, and will stop getting updates.

## Considered Options

* **v5 for now**  
  Good: no proprietary terms, and caching as today.
  Bad: no new fixes; the versions check shows v6 as available, which is accepted.
* **v6 with caching switched off**  
  Good: current, with no proprietary terms.
  Bad: every run downloads every dependency again, so CI is slower.
* **v6 with caching**  
  Good: current and fast.
  Bad: the repo's CI then depends on proprietary terms.

## Decision Outcome

Chosen by the airgap maintainers in discussion: v5 for now. Looked at again when v5 stops working, or a security fix
comes only in v6.

### Consequences

* Good: CI keeps its cache and its open terms.
* Bad: the action is held back on purpose; a security fix only in v6 reopens this decision.

## More Information

* Builds on: [The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues](0007-the-code-is-held-to-strict-quality-checks-run-by-the-build-and-pushes-go-through-a-script-that-syncs-the-issues.md)
* Later decisions that build on this: none yet
* Background: [AGENTS.md](../../AGENTS.md)
