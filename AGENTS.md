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
| `agent/`, `junit/`, `gradle-plugin/`, `maven-extension/` | The agent, the JUnit Platform listener and extension, the Gradle plugin, and the Maven core extension |
| `examples/` | Old services run end to end under the agent, with their approved reports (added as the work starts) |
| `maintainer-tools/` | Repo checks (`./gradlew check`, `buildDecisionIndexes`, `fixDecisionLinks`), the push script, and the hook that refuses a plain `git push` |
| `tools/backlog-import/` | Loads the backlog into the issue tracker |
| [`tools/try-it/airgap.init.gradle`](tools/try-it/airgap.init.gradle) | Runs any Gradle build's tests under the agent, from outside, without changing that build |
| [`tools/try-it/airgap-mvn.sh`](tools/try-it/airgap-mvn.sh) | The same for any Maven build, through the Maven extension |
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
- mutation testing must show the tests catch nearly every bug planted in the refusal and reporting code;
- before each release, and before releasing any new hook, real open-source projects' tests are run under the agent
  with the try-it script, on the JDKs they were written for, and compared with a run without it. A failure is either
  a call the agent should refuse, or a bug to fix.

The Gradle plugin's integration tests run a sample build on the latest Gradle 5, 6, 7, 8 and 9, each on a JDK it
supports. Where Gradle can't download distributions, pass `-Pairgap.gradleDistributions=<folder of gradle-<version>-bin.zip>`.
They run without this repo's agent attached, since TestKit starts Gradle processes and downloads Gradle; the sample
builds themselves run under the agent, through the plugin.

The Maven extension's integration tests run sample builds on the latest Maven 3.6, 3.8, 3.9 and 4, on JDK 17. Each
Maven comes from Maven Central as an ordinary artifact; the samples' plugins download into
`maven-extension/build/maven-it-repo`. Each build's log is in `maven-extension/build/maven-it-work/<version>/<sample>/`.

The README is written for people using the agent. Keep build, quality and contribution details here, and keep version
numbers out of it.

## Code standards
Enforced by `./gradlew check`:

| Standard | Enforced by |
|---|---|
| Line coverage at least 80% per project; and in the agent, PIT must catch at least 90% of the bugs it plants in the code that decides, refuses and reports calls (`min-mutation-score` in the version catalog) | JaCoCo; PIT |
| No more than **three parameters** for any method or constructor. Use a Lombok `@Builder` instead | Checkstyle, PMD |
| No record with more than three components | Checkstyle |
| A test with more than one assertion wraps them in `assertAll(...)` | PMD |
| Logging through SLF4J; no `System.out`, no `java.util.logging`. **In the agent itself**, no logging library at all: it writes only its report, so it can't clash with the application's own logging | forbidden-APIs check; review |
| Time from an injected `java.time.Clock` | forbidden-APIs check |
| No unsafe, deprecated, internal or non-portable JDK APIs, with no exceptions. The JDK internals the hooks rewrite are named only as text, in `Hook.java`, each with the JDKs it applies to, so no compiled code uses them | forbidden-APIs bundled signatures, on every source set; the self-check and `jdkRoutesOnJdk<version>` |
| Methods short and simple: cyclomatic complexity 10 or less, cognitive complexity 15 or less, 50 lines or fewer | Checkstyle, PMD |
| No copy-pasted code | PMD's copy-paste detector |
| No bug patterns or security bugs | SpotBugs with FindSecBugs |
| Dependencies kept current | Ben Manes versions plugin |
| **The agent and the JUnit parts compile to Java 8** (`--release 8`) and are tested on JDK 8, 11, 17, 21 and 25 | The build's toolchains |
| **Every hook has a test on every supported JDK**, and the agent checks its hooks at start | [Every hook is proved on every supported JDK](docs/decisions/0008-every-hook-is-proved-on-every-supported-jdk-and-the-agent-checks-its-own-hooks-at-start.md) |
| **Every public route in the JDK to a risky call passes a hook,** or is on the reviewed list with why it's safe | `jdkRoutesOnJdk<version>`, reading each JDK's own classes; `agent/src/jdkRoutes/resources/example/airgap/routes/reviewed-routes.txt` |
| **The operating system sees nothing the agent didn't report** | `checkOsTrace`, run in CI on the agent's tests under strace |
| Versions only in [`gradle/libs.versions.toml`](gradle/libs.versions.toml); ids under `io.github.fiftieshousewife` | Review |

## Safety
This repo's tests make risky calls on purpose, always to addresses reserved for documentation (`192.0.2.1`) or names
that can't resolve, and always with the agent refusing them. Never point a test at a real host.

The repo runs its own builds under its own agent: the JUnit part's tests, the repo checks' tests and the repo checks
themselves run with it attached, and fail on any risky call (see
[This repo runs its own builds under its own agent, and fails on any risky call](docs/decisions/0009-this-repo-runs-its-own-builds-under-its-own-agent-and-fails-on-any-risky-call.md)).

## Releasing
A release publishes `airgap-agent`, `airgap-junit`, `airgap-maven-extension` and `airgap-gradle-plugin` to Maven
Central, the plugin to the
Gradle Plugin Portal, and a GitHub release. Each jar goes with its sources, Javadoc, a CycloneDX software bill of
materials, and PGP signatures. The shared settings are in
[`build-logic/src/main/kotlin/io.github.fiftieshousewife.airgap-publishing.gradle.kts`](build-logic/src/main/kotlin/io.github.fiftieshousewife.airgap-publishing.gradle.kts).

1. Set `version=` in [`gradle.properties`](gradle.properties) and the defaults in
   [`tools/try-it/airgap.init.gradle`](tools/try-it/airgap.init.gradle) and
   [`tools/try-it/airgap-mvn.sh`](tools/try-it/airgap-mvn.sh), write what's new for users in
   `docs/releases/<version>.md` (see [the release notes](docs/releases/)), and push with `sync.sh`.
2. Run `maintainer-tools/release/release.sh <version>`. It tags `main` and pushes the tag.
3. [The release workflow](.github/workflows/release.yml) runs the whole build first (every JDK, every Gradle), and
   publishes only if it passes.

The workflow needs the repository secrets it lists at its top: a Central Portal user token, a signing key and its
passphrase, and a Gradle Plugin Portal API key. To check a release without publishing it, run
`./gradlew nmcpZipAggregation` (the Maven Central bundle, in `build/nmcp/zip/`) and
`./gradlew :gradle-plugin:publishPlugins --validate-only`.

## Before you finish
Run `./gradlew check`. It must pass. Where Gradle's worker processes can't connect back to Gradle, as in some AI
assistants' sandboxes, PMD and Checkstyle fail to start; run them as plain programs instead, with the same rules, over
every source set: `./gradlew -I maintainer-tools/quality-cli/quality-cli.init.gradle pmdCli checkstyleCli`. Push only with `maintainer-tools/sync/sync.sh "What changed"`, which syncs the
issues and backlog page before pushing; never with a plain `git push`.
