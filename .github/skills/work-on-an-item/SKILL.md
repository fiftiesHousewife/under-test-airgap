---
name: work-on-an-item
description: Pick up a work item from this repo's backlog (plan/airgap-backlog.yaml, imported into the issue tracker) and finish it. Use when asked to "do the next item", "work the backlog", or to work on a named item.
---

# Working on an item

## 1. Pick an item
Take the item named, or the first open item in [plan/airgap-backlog.yaml](../../../plan/airgap-backlog.yaml) whose
`blocked_by` items are all done, in horizon order (now, next, later). Read its `done_when` list: that is the finish
line.

## 2. Read before writing
- [The design](../../../docs/design.md) section the item touches, and the decision records it links to.
- For a hook: which JDK versions it covers, and the test that proves it on each.

## 3. Do the work
| `done_by` | What you do | Finish |
|---|---|---|
| **tool** | Build or change a tool or the build, with tests. | `./gradlew check` passes. |
| **ai** | Draft code, tests or documents for the person to review. | `./gradlew check` passes; the person reviews. |
| **person** | Prepare what the person needs to decide, then ask. | The person decides; record it with the write-a-decision-record skill. |

Tests come first, and make the risky call for real against `192.0.2.1` or a name that can't resolve, with the agent
refusing it. Never point a test at a real host.

## 4. Finish
- Tick off every `done_when` line, and set `status: "done"` on the item, with a short `notes:` saying what was done.
- Run `./gradlew check`, then commit and `git push`. The issues and `docs/BACKLOG.md` update in GitHub Actions; pull
  before the next change.
