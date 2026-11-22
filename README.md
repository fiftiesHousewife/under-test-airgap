# under-test-airgap

[![Build](https://github.com/fiftiesHousewife/under-test-airgap/actions/workflows/build.yml/badge.svg)](https://github.com/fiftiesHousewife/under-test-airgap/actions/workflows/build.yml)
[![Licence: Apache-2.0](https://img.shields.io/badge/licence-Apache--2.0-blue)](LICENSE)
![Runs on JDK 8 to 25](https://img.shields.io/badge/runs%20on-JDK%208%20to%2025-blue)
![Built with JDK 25](https://img.shields.io/badge/built%20with-JDK%2025-blue)
![Gradle 9](https://img.shields.io/badge/Gradle-9-02303A)
![JUnit Platform](https://img.shields.io/badge/JUnit-5%20and%206-25A162)
![Mutation tested with PIT](https://img.shields.io/badge/mutation%20tested-PIT-orange)
[![Decision records](https://img.shields.io/badge/decision%20records-MADR-informational)](docs/decisions/README.md)

**Run old Java code as if its network cable were pulled, and find out everything it tried to reach.**

> **Status:** version 0.1.0 refuses and reports host lookups, TCP and UDP connections (through sockets, NIO channels
> and asynchronous channels) and starting programs, proved on JDK 8, 11, 17, 21 and 25. JNDI, `System.exit`, native
> libraries, file writes, Spring's settings and a settings file follow; [the design](docs/design.md) has the whole
> plan, and the work is in [plan/airgap-backlog.yaml](plan/airgap-backlog.yaml).

## The problem it solves

Imagine being handed an old Java service that nobody on the team has run. Its tests don't pass. Its
`application.properties` might point at the production database, or it might not, and nobody can say for sure.
Somewhere in it, a scheduled job may send email to customers. You need to run it, because you can't test code you
can't run, but you'd rather not find out what it does by watching it do it.

The safe answer is to pull the network cable. On a corporate laptop you can't: there is no cable, the laptop runs
Windows, you don't have administrator rights, there's no container runtime, and nothing can be installed without a
security review. The software equivalents of pulling the cable (containers, operating-system sandboxes, firewall
rules, packet capture) all need exactly what those laptops withhold. Inside the JVM there is no help either: the JDK's
Security Manager, which could refuse connections by policy, is disabled from JDK 24, and there is no maintained "no
network" tool for the JVM like WebMock for Ruby or pytest-socket for Python.

## What the airgap agent does

The airgap agent is a small Java agent: a jar named on the JVM's command line. It needs nothing installed and no
rights beyond running Java, and it works on JDK 8 and every version since.

As the JVM loads its own classes, the agent rewrites the handful of places in the JDK where risky calls really happen:
host lookups, network connections, JNDI and LDAP lookups, starting other programs, loading native code, and
`System.exit`. Because it works at that low level, every library a program might use, from old JDBC drivers to Netty,
goes through the agent's check. Calls to the machine itself are allowed. Calls to anything else are refused with the
exception an unplugged machine would give (`UnknownHostException`, `ConnectException`), so the code takes the same path
it would take offline. And every call, refused or allowed, is written to a report.

The report is half the point. For each call it says what was tried, which test was running, which line of the
program's own code made the call, and, for Spring applications, which setting supplied the address and in which file
and line. A first run of an old service under the agent gives you something nobody had before: a list of everything
the code wants to talk to.

```text
OrderServiceTest > placesAnOrder() FAILED
    java.net.UnknownHostException: db.prod.example.com (refused by the airgap agent)

build/airgap/calls.jsonl:
{"kind":"host-lookup","target":"db.prod.example.com","outcome":"refused",
 "test":"OrderServiceTest.placesAnOrder",
 "caller":"com.example.orders.OrderRepository.connect(OrderRepository.java:41)",
 "setting":{"name":"spring.datasource.url","from":"application.yml:12"}}
```

From there the work is clear: each refused call is something the code depends on, to be listed and given a stand-in
in the test settings, until a run's report comes back clean.

## Using version 0.1.0

With Gradle, give the test runs the agent, and the tests the JUnit listener:

```kotlin
val airgap: Configuration by configurations.creating

dependencies {
    airgap("io.github.fiftieshousewife.airgap:airgap-agent:0.1.0")
    testImplementation("io.github.fiftieshousewife.airgap:airgap-junit:0.1.0")
}

tasks.test {
    val agent = files(airgap)
    val report = layout.buildDirectory.file("airgap/calls.jsonl")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-javaagent:${agent.singleFile}=report=${report.get().asFile}")
    })
}
```

On any JVM, the agent's settings follow `=` on its command line, separated by `;`:

| Setting | What it does |
|---|---|
| `mode=refuse` | Refuse every risky call that isn't allowed, and report it (the default); `report` lets calls through and reports them; `off` does nothing |
| `report=build/airgap/calls.jsonl` | Where the report goes (the default shown) |
| `allow=localhost-stand-in:8089,process:git` | Stand-ins to allow: `host`, `host:port` or `process:name`. The machine itself is always allowed |
| `run=test` | What kind of JVM this is, for the report: `test`, `application` or `build` |
| `project=com.example.` | Package prefixes of the project's own code, so the report names the caller exactly |

A test allows a stand-in it relies on with `@AllowCalls("wiremock.example:8089")`.

## Why "airgap", and what it doesn't promise

An air gap usually means a machine with no network connection at all. The airgap agent is narrower, and it's worth
being plain about how. It works inside the JVM it guards, so code written on purpose to get past it (with native code,
or tricks with the JDK's internals) could. It's built for the real risk in an organisation's own old code: ordinary
calls made by accident, because a configuration file says so. Where code can't be trusted that far, real network
separation is still the answer, such as a first run on CI runners that can't reach production. The reasoning is in
[The airgap agent guards against accidental calls from old code, not against hostile code](docs/decisions/0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md).

## How it fits into a build

Nobody adds the agent by hand. The build wiring, for Gradle and for Maven, adds it to every JVM that runs a project's
code: the test runs, the application when it's started, and the build itself, since an old project's build scripts
are its code too. A JUnit Platform listener, registered automatically, tells the agent which test is running, and a
JUnit extension lets a test allow a stand-in it relies on, such as `@AllowCalls("localhost:8089")` for a local
WireMock. Projects still on JUnit 4 move onto the JUnit Platform first, and their tests run unchanged.

## Why it can be trusted

A tool like this fails in the worst possible way: silently. If a JDK update changed an internal method and a hook
stopped matching, calls would go through and nothing would look wrong. So testing is the centre of this project, not
an afterthought:

- every hook is proved on every supported JDK (8, 11, 17, 21 and 25);
- every kind of call is made through every common route, from plain sockets to Netty and JDBC drivers;
- each test checks the refusal came at once, before a connection could even have been attempted;
- at start, the agent checks that every hook it expects for the running JDK is in place, and refuses to run if not;
- example old services are run end to end, and their reports must match saved, approved ones;
- mutation testing must show the tests catch nearly every bug planted in the refusal and reporting code.

The code is held to strict quality checks, all run by `./gradlew check`: test coverage, Checkstyle, PMD and its
copy-paste detection, SpotBugs with FindSecBugs, forbidden JDK APIs, and current dependency versions. See
[the code standards](AGENTS.md#code-standards).

## Where to go next

- [The design](docs/design.md) covers every risky call and where it's caught, the report, the allowlist, Spring, other
  agents, approval inside a company, testing and the limits.
- [The decision records](docs/decisions/README.md) explain why it's built and not adopted, the promise it makes, how a
  refused call behaves, how its parts fit, and how it's tested.
- [AGENTS.md](AGENTS.md) is for anyone working on it, and for the AI assistants they direct.
- [GLOSSARY.md](GLOSSARY.md) explains the words used here.

## Licence

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
