---
status: accepted
decision-makers: Airgap maintainers
group: How this repo works
---

# The backlog syncs to the issues in GitHub Actions after each push, and pushes are plain git pushes after the checks

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

[The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues](0007-the-code-is-held-to-strict-quality-checks-run-by-the-build-and-pushes-go-through-a-script-that-syncs-the-issues.md)
made every push go through `maintainer-tools/sync/sync.sh`. It ran `./gradlew check`, imported the backlog into the
issues with this repo's own copy of the importer, refreshed `docs/BACKLOG.md`, and pushed; a hook refused a plain
`git push`. Each of the maintainers' repos kept its own copy of the script and the importer, and the copies drifted.
The import also needed a token with rights to write issues on the maintainer's machine. Where should the sync run?

## Considered Options

* **The shared backlog workflow in GitHub Actions, run after a push that changes the backlog**
  Good: one importer for every repo, kept in the toolkit repo `fiftiesHousewife/under-test`; it uses the workflow's
  own token, so no token with rights to write issues sits on a machine; a push needs nothing but git.
  Bad: the issues and the backlog page update a minute after the push, not before; Actions commits the page, so the
  next change starts with a pull; nothing stops a push without `./gradlew check` except the rule and CI.
* **Keep the push script and its hook in this repo**
  Good: the issues are up to date the moment the push lands, and the hook enforces the checks.
  Bad: a copy of the importer to keep in step with every other repo's; a token on every maintainer's machine.

## Decision Outcome

Chosen by the airgap maintainers in discussion: the shared workflow. `.github/workflows/sync-backlog.yml` calls
`fiftiesHousewife/under-test/.github/workflows/backlog-sync.yml` on each push to `main` that changes
`plan/airgap-backlog.yaml`, and can be run by hand to post every issue link again. To push: run `./gradlew check`,
commit, and `git push`. The importer still matches issues by each item's key, so keys never change. This replaces the
push rule of the record above; its quality checks stand. `maintainer-tools/sync/`, `maintainer-tools/git-hooks/` and
`tools/backlog-import/` are removed, and so is `maintainer-tools/quality-cli/`, a way to run Checkstyle and PMD in
AI assistants' sandboxes that the assistants' own settings now make unneeded.

### Consequences

* Good: one importer, maintained once, for every repo; no token on maintainers' machines.
* Good: a push is a plain `git push`.
* Bad: the issues trail the push by the length of a workflow run.
* Bad: the checks before a push are a rule, not a hook; CI runs them again on every push.

## More Information

* Builds on: [The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues](0007-the-code-is-held-to-strict-quality-checks-run-by-the-build-and-pushes-go-through-a-script-that-syncs-the-issues.md), whose push rule it replaces
* Later decisions that build on this: none yet
* Background: [AGENTS.md](../../AGENTS.md#before-you-finish)
