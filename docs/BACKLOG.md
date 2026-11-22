# Backlog

Open work for this repository, generated from the issue tracker by [tools/backlog-import](../tools/backlog-import/README.md) after each import. Do not edit by hand.

Ordered by horizon (now, next, later), then items that are ready before items waiting on others.

## [The airgap agent refuses and reports every ordinary risky call, on every supported JDK](https://github.com/fiftiesHousewife/under-test-airgap/issues/1)

| | Item | Type | Owner | Done by | Waiting on |
|---|---|---|---|---|---|
| #16 | [The report: one line of JSON per call, safe to write from several JVMs at once, with a summary at the end](https://github.com/fiftiesHousewife/under-test-airgap/issues/16) | deliverable | Technical Architect | ai | – |
| #18 | [Every common route to the network is proved refused: the JDK's HTTP client, Apache HttpClient, OkHttp, Netty, JDBC drivers](https://github.com/fiftiesHousewife/under-test-airgap/issues/18) | deliverable | Technical Architect | ai | – |
| #20 | [The allowlist from testing/airgap.yaml, checked against its schema](https://github.com/fiftiesHousewife/under-test-airgap/issues/20) | deliverable | Technical Architect | ai | – |
| #21 | [Hooks for JNDI and LDAP lookups, starting processes, loading native code and ending the JVM](https://github.com/fiftiesHousewife/under-test-airgap/issues/21) | deliverable | Technical Architect | ai | – |
| #24 | [The agent works alongside JaCoCo, PIT and Mockito's inline mock maker](https://github.com/fiftiesHousewife/under-test-airgap/issues/24) | deliverable | Technical Architect | ai | – |
| #22 | [Writes to files outside the project are reported, and refused when the settings say so](https://github.com/fiftiesHousewife/under-test-airgap/issues/22) | deliverable | Technical Architect | ai | [The report: one line of JSON per call, safe to write from several JVMs at once, with a summary at the end](https://github.com/fiftiesHousewife/under-test-airgap/issues/16) |
| #23 | [For Spring applications: the setting behind each refused address, the active profiles, and a stop on production profiles](https://github.com/fiftiesHousewife/under-test-airgap/issues/23) | deliverable | Technical Architect | ai | [Hooks for JNDI and LDAP lookups, starting processes, loading native code and ending the JVM](https://github.com/fiftiesHousewife/under-test-airgap/issues/21) |
| #25 | [Example old services (Spring with Gradle, Spring with Maven, plain Java) run end to end, with approved reports](https://github.com/fiftiesHousewife/under-test-airgap/issues/25) | deliverable | Test Quality Lead | ai | [Gradle wiring: the agent on every test run, application start and the build itself](https://github.com/fiftiesHousewife/under-test-airgap/issues/26), [Maven wiring: the agent on Surefire, Failsafe, application starts and Maven itself](https://github.com/fiftiesHousewife/under-test-airgap/issues/27), [For Spring applications: the setting behind each refused address, the active profiles, and a stop on production profiles](https://github.com/fiftiesHousewife/under-test-airgap/issues/23) |

## [This repo keeps itself in order: its checks, CI, decisions and releases](https://github.com/fiftiesHousewife/under-test-airgap/issues/2)

| | Item | Type | Owner | Done by | Waiting on |
|---|---|---|---|---|---|
| #13 | [A reviewed list of the JDK internals the hooks name, as exceptions to the forbidden-API check](https://github.com/fiftiesHousewife/under-test-airgap/issues/13) | deliverable | Toolkit Owner | ai | – |
| #14 | [Mutation testing with PIT holds the refusal and reporting code to a high bar](https://github.com/fiftiesHousewife/under-test-airgap/issues/14) | deliverable | Technical Architect | tool | – |

## [The airgap agent is ready for a company to approve and use, with Gradle or Maven](https://github.com/fiftiesHousewife/under-test-airgap/issues/3)

| | Item | Type | Owner | Done by | Waiting on |
|---|---|---|---|---|---|
| #26 | [Gradle wiring: the agent on every test run, application start and the build itself](https://github.com/fiftiesHousewife/under-test-airgap/issues/26) | deliverable | Technical Architect | ai | – |
| #27 | [Maven wiring: the agent on Surefire, Failsafe, application starts and Maven itself](https://github.com/fiftiesHousewife/under-test-airgap/issues/27) | deliverable | Technical Architect | ai | – |
| #28 | [Releases publish the agent jar with a software bill of materials, ready for a security review](https://github.com/fiftiesHousewife/under-test-airgap/issues/28) | deliverable | Toolkit Owner | tool | [Example old services (Spring with Gradle, Spring with Maven, plain Java) run end to end, with approved reports](https://github.com/fiftiesHousewife/under-test-airgap/issues/25) |
