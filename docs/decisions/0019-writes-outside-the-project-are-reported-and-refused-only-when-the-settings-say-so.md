---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# Writes outside the project are reported, and refused only when the settings say so

**Decided by:** Airgap maintainers · **Status:** accepted

## Context and Problem Statement

Old batch jobs write to, move and clean up fixed folders, such as `C:\data\out` or `/var/spool/orders`. On an
engineer's machine those folders may hold real files, or be another system's. The agent should find these writes
before they surprise anyone. But the file APIs are the JVM's busiest: every log line, build output and temporary
file goes through them. So a hook there costs more, and a wrong one breaks more, than any network hook.

Three questions:

* What counts as a write?
* Which folders count as the project's?
* Should a write be refused, or only reported?

## Considered Options

### What counts as a write

* **Opening a file to write, create or append, and also deleting, renaming or moving files and making folders**
  This covers `FileOutputStream`, `RandomAccessFile` opened to write, and `Files`' streams and channels opened to
  write. It also covers `File.delete`, `renameTo`, `mkdir` and `createNewFile`, and `Files`' delete, move, copy,
  link and directory calls.
  Good: a job that moves or deletes files outside the project is found, and a delete is the hardest of these to
  undo.
  Bad: more hooks, each one on the JDK's busiest code.
* **Only opening a file to write**
  Good: fewer hooks and less noise.
  Bad: a job that deletes or moves files goes unseen.

### Which folders are the project's

* **The build's root folder, the system's temporary folder, and folders listed in `testing/airgap.yaml`**
  The plugin passes the build's root folder; started any other way, it is the folder the JVM starts in. Each listed
  folder (`write_folders`) carries its reason, as `allow` entries do. The agent's own report folder counts too.
  Writes inside these go ahead and aren't recorded.
  Good: a multi-project build's shared build folders don't fill the report, and a folder the team has approved can
  be named once.
  Bad: a folder listed by mistake hides its writes.
* **The folder the JVM starts in, and the temporary folder, only**
  Bad: a subproject writing to the root's build folder is reported, and there's no way to approve a folder.

### Refuse, or report

* **Report by default; refuse when `file_writes: refuse` is set and the mode is `refuse`**
  Good: a first run finds every write without breaking runs that would otherwise teach something.
  Bad: until a team turns refusing on, a write outside the project still happens.
* **Refuse by default**
  Bad: many old jobs would fail at their first log file or export, before the run shows anything else.

## Decision Outcome

Chosen by the airgap maintainers in discussion:

* opening to write, create or append, plus deleting, renaming, moving and making folders, all count as writes;
* the project's folders are the build's root, the temporary folder, the agent's report folder and the folders listed
  in `write_folders`;
* writes are reported by default, and refused when `file_writes: refuse` is set and the mode is `refuse`.

A write by the build tool's own classes, such as Gradle unpacking its native libraries into the Gradle user home, goes
ahead without being recorded, as the build tool's own native libraries do. A refused open fails as an unwritable
folder fails: `FileNotFoundException` from `java.io`, `AccessDeniedException` from `java.nio.file`, and `false` from
`File`'s methods that answer with one.

The file hooks are risky, so they are not released until they have run real projects' tests on every supported JDK.
At least Spring PetClinic, the RealWorld example app, Halo and the older Spring guides must give the same results as
without the hooks, and every write those runs report must be reviewed.

### Consequences

* Good: a report shows every file the code wrote, moved or deleted outside the project, with the line that did it.
* Good: a team can turn refusing on once its tests no longer write outside the project.
* Bad: the hooks sit on the JDK's busiest calls, so each one must decide quickly whether a path is inside the
  project, and must never fail on a path it can't read.
* Bad: on macOS, `/var` and `/tmp` are links to `/private/var` and `/private/tmp`, so each folder is compared in its
  given form and its real form.

## More Information

* Builds on: [Refused calls fail as an unplugged machine would, never with made-up success](0003-refused-calls-fail-as-an-unplugged-machine-would-never-with-made-up-success.md)
* Builds on: [Native libraries are refused unless allowed, except the JDK's and the build tool's own](0012-native-libraries-are-refused-unless-allowed-except-the-jdks-and-the-build-tools-own.md)
* Later decisions that build on this: none yet
* Background: [the design: what counts as a risky call](../design.md#what-counts-as-a-risky-call-and-where-its-caught)
