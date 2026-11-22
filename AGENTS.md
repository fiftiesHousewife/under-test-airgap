# Agent guide

Guidance for any AI coding assistant working in this repo, and for the people directing it.

**What this repo is:** the airgap agent, a Java agent that refuses and reports the risky calls old Java code makes, so
it can be run safely on a locked-down machine. Start with [the design](docs/design.md). How the repo works is settled
in [The code is held to strict quality checks run by the build, and pushes go through a script that syncs the issues](docs/decisions/0007-the-code-is-held-to-strict-quality-checks-run-by-the-build-and-pushes-go-through-a-script-that-syncs-the-issues.md).

**Keep it generic.** Nothing about any one organisation belongs here. Name a product only as an option or an example.

## Words and references
- Follow [GLOSSARY.md](GLOSSARY.md): plain English, no repurposed words, industry terms explained on first use.
- Refer to anything by its **name, as a link**, never by a code or number.
- No dates in titles or text: source control records when things changed.
- Every YAML and JSON format has a JSON Schema in [`schema/`](schema/), referenced from each YAML file's first line.

## Where things are
| Path | What |
|---|---|
| [`docs/design.md`](docs/design.md) | How the agent works, why, and its limits |
| [`docs/decisions/`](docs/decisions/README.md) | This repo's decisions, in MADR form |
| [`plan/airgap-backlog.yaml`](plan/airgap-backlog.yaml) | The work, imported into this repo's issues and shown on `docs/BACKLOG.md` after each push |
| [`schema/`](schema/) | Schemas: the report, the repo's settings, the backlog |
| `agent/`, `junit/`, `build-wiring/` | The agent, the JUnit Platform listener and extension, and the Gradle and Maven wiring (added as the work starts) |
| `examples/` | Old services run end to end under the agent, with their approved reports (added as the work starts) |
| `maintainer-tools/` | Repo checks (`./gradlew check`, `buildDecisionIndexes`, `fixDecisionLinks`), the push script, and the hook that refuses a plain `git push` |
| `tools/backlog-import/` | Loads the backlog into the issue tracker |
| [`tools/try-it/airgap.init.gradle`](tools/try-it/airgap.init.gradle) | Runs any Gradle build's tests under the agent, from outside, without changing that build |
| `build-logic/`, `config/quality/` | Gradle conventions and the quality-check rules |

## Skills
| Skill | Use it to |
|---|---|
| [work-on-an-item](.github/skills/work-on-an-item/SKILL.md) | Pick up a work item and finish it |
| [write-a-decision-record](.github/skills/write-a-decision-record/SKILL.md) | Record a decision settled in conversation |
| grilling (installed by APM) | Interview a person about a decision. In Claude Code, ask one question at a time with the AskUserQuestion tool |
| architecture-decision-records (installed by APM) | The mechanics of writing a MADR record |

External skills are declared in [`apm.yml`](apm.yml), pinned, and installed with `apm install`. For Claude Code, this
repo's own skills are linked into `.claude/skills/`.

## How the repo is built and tested
The repo builds with Gradle on JDK 25, through toolchains, and tests on JDK 8, 11, 17, 21 and 25. Its JUnit parts
support JUnit 5 and 6 on the JUnit Platform. Decisions are recorded in MADR form. The work, and what the agent
catches today and next, is in [the backlog](plan/airgap-backlog.yaml).

Testing is the centre of this project, because a hook that stops matching fails silently. Beyond what the README
promises users (the self-check at start, every hook on every JDK, every common route, refusal before any connection):

- example old services are run end to end, and their reports must match saved, approved ones;
- mutation testing must show the tests catch nearly every bug planted in the refusal and reporting code.

The README is written for people using the agent. Keep build, quality and contribution details here, and keep version
numbers out of it.

## Code standards
Enforced by `./gradlew check`:

| Standard | Enforced by |
|---|---|
| Line coverage at least 80% per project; the agent's refusal and reporting code much higher, by mutation testing | JaCoCo; PIT |
| No more than **three parameters** for any method or constructor. Use a Lombok `@Builder` instead | Checkstyle, PMD |
| No record with more than three components | Checkstyle |
| A test with more than one assertion wraps them in `assertAll(...)` | PMD |
| Logging through SLF4J; no `System.out`, no `java.util.logging`. **In the agent itself**, no logging library at all: it writes only its report, so it can't clash with the application's own logging | forbidden-APIs check; review |
| Time from an injected `java.time.Clock` | forbidden-APIs check |
| No unsafe, deprecated, internal or non-portable JDK APIs, **except** the JDK internals the agent's hooks name, each listed with the JDK range it applies to | forbidden-APIs bundled signatures, with a reviewed exception list |
| Methods short and simple: cyclomatic complexity 10 or less, cognitive complexity 15 or less, 50 lines or fewer | Checkstyle, PMD |
| No copy-pasted code | PMD's copy-paste detector |
| No bug patterns or security bugs | SpotBugs with FindSecBugs |
| Dependencies kept current | Ben Manes versions plugin |
| **The agent and the JUnit parts compile to Java 8** (`--release 8`) and are tested on JDK 8, 11, 17, 21 and 25 | The build's toolchains |
| **Every hook has a test on every supported JDK**, and the agent checks its hooks at start | [Every hook is proved on every supported JDK](docs/decisions/0008-every-hook-is-proved-on-every-supported-jdk-and-the-agent-checks-its-own-hooks-at-start.md) |
| Versions only in [`gradle/libs.versions.toml`](gradle/libs.versions.toml); ids under `io.github.fiftieshousewife` | Review |

## Safety
This repo's tests make risky calls on purpose, always to addresses reserved for documentation (`192.0.2.1`) or names
that can't resolve, and always with the agent refusing them. Never point a test at a real host.

The repo runs its own builds under its own agent: the JUnit part's tests, the repo checks' tests and the repo checks
themselves run with it attached, and fail on any risky call (see
[This repo runs its own builds under its own agent, and fails on any risky call](docs/decisions/0009-this-repo-runs-its-own-builds-under-its-own-agent-and-fails-on-any-risky-call.md)).

## Before you finish
Run `./gradlew check`. It must pass. Push only with `maintainer-tools/sync/sync.sh "What changed"`, which syncs the
issues and backlog page before pushing; never with a plain `git push`.
