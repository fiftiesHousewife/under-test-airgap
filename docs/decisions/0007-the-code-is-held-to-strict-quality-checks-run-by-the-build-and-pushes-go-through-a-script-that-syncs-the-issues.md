---
status: accepted
decision-makers: Airgap maintainers
group: How this repo works
---

# The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion); its rules replaced by [The code-quality rules come from the shared java-conventions plugin, pinned to a version](0023-the-code-quality-rules-come-from-the-shared-java-conventions-plugin-pinned-to-a-version.md), and its push rule by [The backlog syncs to the issues in GitHub Actions after each push, and pushes are plain git pushes after the checks](0021-the-backlog-syncs-to-the-issues-in-github-actions-after-each-push-and-pushes-are-plain-git-pushes-after-the-checks.md)

## Context and Problem Statement

The agent runs inside other people's JVMs, on code nobody trusts yet, and a security team must approve it. Its own code
has to be beyond reproach, and the work on it has to be visible to whoever is waiting for it.

## Considered Options

* **Strict checks in `./gradlew check` (coverage, Checkstyle, PMD and copy-paste detection, SpotBugs with FindSecBugs,
  forbidden JDK APIs, current dependencies), decision records for every settled choice, a schema for every YAML and
  JSON format, a backlog file imported into the issue tracker, and pushes only through a script that checks the build
  and syncs the issues first**
  Every standard is checked by a machine, and the issues never fall behind the backlog
* **Lighter checks and ad hoc issues**
  Quicker at the start; standards drift, and a security review finds them

## Decision Outcome

Chosen: the first option. The standards are listed in [AGENTS.md](../../AGENTS.md#code-standards); pushes use
`maintainer-tools/sync/sync.sh`, and a hook refuses a plain `git push`.

### Consequences

* Good: one command proves the repo is in order, and a reviewer can see why each choice was made.
* Bad: the agent's hooks need a reviewed list of exceptions to the forbidden-API check, since they name JDK internals
  on purpose.

## More Information

* Builds on: nothing (a foundation decision)
* Later decisions that build on this: [The backlog syncs to the issues in GitHub Actions after each push, and pushes are plain git pushes after the checks](0021-the-backlog-syncs-to-the-issues-in-github-actions-after-each-push-and-pushes-are-plain-git-pushes-after-the-checks.md), [Every hook is proved on every supported JDK, and the agent checks its own hooks at start](0008-every-hook-is-proved-on-every-supported-jdk-and-the-agent-checks-its-own-hooks-at-start.md), [The code-quality rules come from the shared java-conventions plugin, pinned to a version](0023-the-code-quality-rules-come-from-the-shared-java-conventions-plugin-pinned-to-a-version.md)
* Background: none
