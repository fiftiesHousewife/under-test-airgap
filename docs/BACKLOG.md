# Backlog

Open work for this repository, generated from the issue tracker by [tools/backlog-import](../tools/backlog-import/README.md) after each import. Do not edit by hand.

Ordered by horizon (now, next, later), then items that are ready before items waiting on others.

## [The airgap agent refuses and reports every ordinary risky call, on every supported JDK](https://github.com/fiftiesHousewife/under-test-airgap/issues/1)

| | Item | Type | Owner | Done by | Waiting on |
|---|---|---|---|---|---|
| #35 | [A native library is the JDK's own only when a JDK class loader can see the class asking for it](https://github.com/fiftiesHousewife/under-test-airgap/issues/35) | decision | Technical Architect | person | – |
| #16 | [The report: one line of JSON per call, safe to write from several JVMs at once, with a summary at the end](https://github.com/fiftiesHousewife/under-test-airgap/issues/16) | deliverable | Technical Architect | ai | – |
| #18 | [Every common route to the network is proved refused: the JDK's HTTP client, Apache HttpClient, OkHttp, Netty, JDBC drivers](https://github.com/fiftiesHousewife/under-test-airgap/issues/18) | deliverable | Technical Architect | ai | – |
| #20 | [The allowlist from testing/airgap.yaml, checked against its schema](https://github.com/fiftiesHousewife/under-test-airgap/issues/20) | deliverable | Technical Architect | ai | – |
| #40 | [What the agent reports and refuses when calls go through an HTTP proxy](https://github.com/fiftiesHousewife/under-test-airgap/issues/40) | decision | Technical Architect | person | – |
| #23 | [For Spring applications: the setting behind each refused address, the active profiles, and a stop on production profiles](https://github.com/fiftiesHousewife/under-test-airgap/issues/23) | deliverable | Technical Architect | ai | – |
| #24 | [The agent works alongside JaCoCo, PIT and Mockito's inline mock maker](https://github.com/fiftiesHousewife/under-test-airgap/issues/24) | deliverable | Technical Architect | ai | – |
| #44 | [The machine's own network addresses count as local, so RMI and JMX inside the JVM work](https://github.com/fiftiesHousewife/under-test-airgap/issues/44) | deliverable | Technical Architect | ai | – |
| #45 | [Each host lookup is checked and reported once, not once per hook it passes](https://github.com/fiftiesHousewife/under-test-airgap/issues/45) | deliverable | Technical Architect | ai | – |
| #22 | [Writes to files outside the project are reported, and refused when the settings say so](https://github.com/fiftiesHousewife/under-test-airgap/issues/22) | deliverable | Technical Architect | ai | [The report: one line of JSON per call, safe to write from several JVMs at once, with a summary at the end](https://github.com/fiftiesHousewife/under-test-airgap/issues/16) |
| #31 | [Opening files on shared drives is refused and reported: \\server\share paths and mapped network drives](https://github.com/fiftiesHousewife/under-test-airgap/issues/31) | deliverable | Technical Architect | ai | [The report: one line of JSON per call, safe to write from several JVMs at once, with a summary at the end](https://github.com/fiftiesHousewife/under-test-airgap/issues/16) |
| #25 | [Example old services (Spring with Gradle, Spring with Maven, plain Java) run end to end, with approved reports](https://github.com/fiftiesHousewife/under-test-airgap/issues/25) | deliverable | Test Quality Lead | ai | [Maven wiring: the agent on Surefire, Failsafe, application starts and Maven itself](https://github.com/fiftiesHousewife/under-test-airgap/issues/27), [For Spring applications: the setting behind each refused address, the active profiles, and a stop on production profiles](https://github.com/fiftiesHousewife/under-test-airgap/issues/23) |

## [This repo keeps itself in order: its checks, CI, decisions and releases](https://github.com/fiftiesHousewife/under-test-airgap/issues/2)

| | Item | Type | Owner | Done by | Waiting on |
|---|---|---|---|---|---|
| #13 | [A reviewed list of the JDK internals the hooks name, as exceptions to the forbidden-API check](https://github.com/fiftiesHousewife/under-test-airgap/issues/13) | deliverable | Toolkit Owner | ai | – |
| #14 | [Mutation testing with PIT holds the refusal and reporting code to a high bar](https://github.com/fiftiesHousewife/under-test-airgap/issues/14) | deliverable | Technical Architect | tool | – |
| #52 | [The CI and release workflows use the current major versions of their GitHub Actions](https://github.com/fiftiesHousewife/under-test-airgap/issues/52) | task | Toolkit Owner | tool | – |
| #51 | [The build logic holds only what this repo uses: no toolkit layer, no logging added then removed, the agent wiring in one place](https://github.com/fiftiesHousewife/under-test-airgap/issues/51) | deliverable | Toolkit Owner | ai | – |

## [The airgap agent is ready for a company to approve and use, with Gradle or Maven](https://github.com/fiftiesHousewife/under-test-airgap/issues/3)

| | Item | Type | Owner | Done by | Waiting on |
|---|---|---|---|---|---|
| #50 | [The design and README describe only what the agent does today, and say what is still to come](https://github.com/fiftiesHousewife/under-test-airgap/issues/50) | deliverable | Technical Architect | ai | – |
| #48 | [How a native: allow entry matches library files whose names carry versions or random ids, as sqlite-jdbc's do](https://github.com/fiftiesHousewife/under-test-airgap/issues/48) | decision | Technical Architect | person | – |
| #27 | [Maven wiring: the agent on Surefire, Failsafe, application starts and Maven itself](https://github.com/fiftiesHousewife/under-test-airgap/issues/27) | deliverable | Technical Architect | ai | – |
| #47 | [@AllowCalls covers a test class's set-up (BeforeAll, Spring's start-up) and is inherited from base and enclosing classes](https://github.com/fiftiesHousewife/under-test-airgap/issues/47) | deliverable | Technical Architect | ai | – |
| #49 | [The try-it init script applies the Gradle plugin, so each test task gets its own report and a missing agent says so](https://github.com/fiftiesHousewife/under-test-airgap/issues/49) | deliverable | Technical Architect | ai | – |
