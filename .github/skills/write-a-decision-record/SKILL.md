---
name: write-a-decision-record
description: Draft or update a decision record in docs/decisions/ of this repo. Use for decision items, open questions, or whenever a conversation settles a choice about the airgap agent or how this repo works.
---

# Writing a decision record

Records follow [MADR](https://adr.github.io/madr/); the architecture-decision-records skill, installed by APM, has the
mechanics. Every settled choice gets a record, in the same piece of work.

1. Copy [the template](../../../docs/decisions/template.md) to `docs/decisions/NNNN-<the-decision-in-kebab-case>.md`,
   numbered after the last record. The title states the decision in full, in plain English. One small decision.
2. Once accepted, a record is never edited; a change is a new record that replaces it, and the old one's status says
   which record replaced it.
3. Front matter: `status`, `decision-makers` (Airgap maintainers), `group` (one already used in
   [the index](../../../docs/decisions/README.md)).
4. **Considered Options** are the real ones, each with what follows from it, good and bad. For anything about running
   on corporate machines, say why each option fails there, with a reference.
5. **Consequences** list the good and the bad.
6. **More Information** links, by title, to the records this builds on, and to sections of [the design](../../../docs/design.md).
7. Decision Outcome: "Proposed: …" or "Not yet chosen." Mark it accepted only when the maintainers chose it in
   conversation (say so) or approved its pull request.
8. Keep [the design](../../../docs/design.md) in step: a decision that changes how the agent works changes the design
   in the same pull request.
9. Rebuild the index with `./gradlew buildDecisionIndexes`, and add or update the decision item in
   [plan/airgap-backlog.yaml](../../../plan/airgap-backlog.yaml) with `decision_record:` pointing at the file.

## Discussing a decision with a person
Use the grilling skill (installed by APM). In Claude Code, ask **one question per AskUserQuestion call**, with the
recommended option first. Write answers into the record only after the person confirms you share an understanding.
