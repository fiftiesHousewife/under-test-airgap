---
status: accepted
decision-makers: Airgap maintainers
group: How this repo works
---

# The code-quality rules come from the shared java-conventions plugin, pinned to a version

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

[The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues](0007-the-code-is-held-to-strict-quality-checks-run-by-the-build-and-pushes-go-through-a-script-that-syncs-the-issues.md)
put the rules in this repo: a convention plugin in `build-logic` and rule files in `config/quality`. The
maintainers' other repos kept copies of the same rules, and the copies drifted. The rules now live in one published
Gradle plugin, `io.github.fiftieshousewife.java-conventions`, stricter than this repo's copy: methods of 20 lines and
complexity 5, files of 150 lines, one assertion statement per test, no comments on fields. Does this repo use it?

## Considered Options

* **Apply the published plugin, pinned to a version, and meet its rules**
  Good: one set of rules, maintained once, with the reasons recorded in that repo; this repo's code meets the same bar
  as the rest. Bad: the stricter rules split many of the agent's classes and methods; the plugin must allow what this
  repo needs: Java 8 bytecode on a Java 25 toolchain, no logging library in the published jars, PIT's floor in `check`,
  and the agent's larger main package.
* **Keep this repo's own copy**
  Good: no rework. Bad: a copy to keep in step, which had already drifted; the rules a reviewer reads in one repo
  aren't the ones applied in another.

## Decision Outcome

Chosen by the airgap maintainers in discussion: the published plugin, pinned in `settings.gradle.kts`.
`build-logic`'s own conventions and `config/quality` are removed; `build-logic` keeps only how the parts are
published and the helper that runs this repo's own tests under the agent. This repo's settings are in each project's
`underTest { }` block: no logging library for the agent, `airgap-junit` and the Maven extension; PIT's floor, run by
`check`, for the agent; 40 classes for the agent's main package, whose hooks reach its package-private parts. The
JDK route check's ASM visitors, which override ASM's own methods of more than three parameters, are left out of the
code-quality checks until the plugin exempts overriding methods. This replaces the record above's rules; its other
parts stand, as amended.

### Consequences

* Good: the same rules as the maintainers' other repos, with a version that changes only on purpose.
* Good: smaller classes, each saying on itself what it does: the hook tables are in `agent.hooks`, the check at start's
  probes in `agent.probes`, the settings file's reading in `agent.settings`.
* Bad: a gap in the plugin is fixed there and released before this repo can use the fix.

## More Information

* Builds on: [The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues](0007-the-code-is-held-to-strict-quality-checks-run-by-the-build-and-pushes-go-through-a-script-that-syncs-the-issues.md)
* Later decisions that build on this: none yet
* Background: [AGENTS.md](../../AGENTS.md#code-standards)
