# under-test-airgap

[![Build](https://github.com/fiftiesHousewife/under-test-airgap/actions/workflows/build.yml/badge.svg)](https://github.com/fiftiesHousewife/under-test-airgap/actions/workflows/build.yml)
[![Licence: Apache-2.0](https://img.shields.io/badge/licence-Apache--2.0-blue)](LICENSE)
![Runs on JDK 8 and later](https://img.shields.io/badge/runs%20on-JDK%208%20and%20later-blue)

**Run old Java code as if its network cable were pulled, and find out everything it tried to reach.**

## The problem it solves

Imagine being handed an old Java service that you've never seen run. Its tests don't pass. Its
`application.properties` might point at the production database, or it might not, and you can't be sure which.
Somewhere in it, a scheduled job may send email to customers. You need to run it, because you can't test code you
can't run, but you'd rather not find out what it does by watching it do it.

The safe answer is to pull the network cable. On a corporate laptop you can't: there is no cable, the laptop runs
Windows, you don't have administrator rights, there's no container runtime, and nothing can be installed without a
security review. The software equivalents of pulling the cable (containers, operating-system sandboxes, firewall
rules, packet capture) all need exactly what those laptops withhold. Inside the JVM there is no help either: the JDK's
Security Manager, which could refuse connections by policy, is disabled in recent JDKs, and there is no maintained
"no network" tool for the JVM like WebMock for Ruby or pytest-socket for Python.

## What the airgap agent does

The airgap agent is a small Java agent: a jar named on the JVM's command line. It needs nothing installed and no
rights beyond running Java, and it works on JDK 8 and every version since.

As the JVM loads its own classes, the agent rewrites the handful of places in the JDK where risky calls really happen.
Because it works at that low level, every library a program might use, from old JDBC drivers to Netty, goes through
the agent's check. Calls to the machine itself are allowed. Calls to anything else are refused with the exception an
unplugged machine would give (`UnknownHostException`, `ConnectException`), so the code takes the same path it would
take offline. And every refused call is written to a report.

| It refuses and reports | Coming |
|---|---|
| Host lookups | JNDI and LDAP lookups |
| TCP connections, through sockets, NIO channels and asynchronous channels | Loading native libraries |
| UDP traffic | `System.exit` |
| Starting other programs | File writes outside the build folder |
| | Naming the Spring setting, and the file and line, that supplied each address |

[The design](docs/design.md) has the whole plan.

## Getting started

You need a Gradle build whose tests you want to run safely, and the agent's jar. Until the agent is in a public
package repository, build it from this repo, which puts it in your local Maven repository:

```bash
git clone https://github.com/fiftiesHousewife/under-test-airgap.git
cd under-test-airgap
./gradlew publishToMavenLocal
```

### 1. Run the tests under the agent

[`tools/try-it/airgap.init.gradle`](tools/try-it/airgap.init.gradle) adds the agent to a build's tests from the
outside, so nothing in that build, or its CI, changes. In the project you want to try:

```bash
./gradlew test --init-script /path/to/under-test-airgap/tools/try-it/airgap.init.gradle
```

To use a jar from somewhere else, add `-Dairgap.agent=<path to the jar>`.

### 2. Read the report

Each project's report goes to its own `build/airgap/calls.jsonl`. No report means no risky calls. A failing test
shows the refusal as an ordinary exception:

```text
OrderServiceTest > placesAnOrder() FAILED
    java.net.UnknownHostException: db.orders.example.com: refused by the airgap agent
```

### 3. Give each dependency a stand-in

Each refused call is something the code depends on. List it, and give it a stand-in in the test settings (an
in-memory database, a local WireMock), until a run's report comes back empty. A test allows a stand-in it relies on
with the JUnit extension:

```java
@AllowCalls("localhost:8089")
class PaymentClientTest { ... }
```

### Adding it to a build for good

To keep the agent on a project's tests, give the test runs the agent and the tests the JUnit listener, which
tells the agent which test made each call:

```kotlin
val airgapVersion = "..." // the version you built or your company approved
val airgap: Configuration by configurations.creating

dependencies {
    airgap("io.github.fiftieshousewife.airgap:airgap-agent:$airgapVersion")
    testImplementation("io.github.fiftieshousewife.airgap:airgap-junit:$airgapVersion")
}

tasks.test {
    val agent = files(airgap)
    val report = layout.buildDirectory.file("airgap/calls.jsonl")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-javaagent:${agent.singleFile}=report=${report.get().asFile}")
    })
}
```

Gradle and Maven plugins that do this in one line are on the way. On any JVM, outside a build, name the agent
directly:

```bash
java -javaagent:airgap-agent.jar="report=build/airgap/calls.jsonl;project=com.example." -jar old-service.jar
```

### Settings

The agent's settings follow `=` on its command line, separated by `;`:

| Setting | What it does |
|---|---|
| `mode=refuse` | Refuse every risky call that isn't allowed, and report it (the default); `report` lets calls through and reports them; `off` does nothing |
| `report=build/airgap/calls.jsonl` | Where the report goes (the default shown) |
| `allow=localhost-stand-in:8089,process:git` | Stand-ins to allow: `host`, `host:port` or `process:name`. The machine itself is always allowed |
| `run=test` | What kind of JVM this is, for the report: `test`, `application` or `build` |
| `project=com.example.` | Package prefixes of your own code, so the report names the line of your code that made the call |

A mistyped setting stops the JVM at start, so it's never silently ignored.

## An example report

A first run of an old order service's tests. Each line of the report is one call, as JSON
([its schema](schema/airgap-report.schema.json)); the stack traces are cut short here:

```json
{"kind":"host-lookup","target":"db.orders.example.com","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.OrderServiceTest.placesAnOrder","caller":"com.example.orders.OrderRepository.connect(OrderRepository.java:41)","stack":["java.base/java.net.InetAddress.getByName(InetAddress.java:1301)","com.example.orders.OrderRepository.connect(OrderRepository.java:41)"]}
{"kind":"tcp","target":"10.20.4.17:25","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.OrderServiceTest.emailsTheCustomer","caller":"com.example.orders.ConfirmationMailer.send(ConfirmationMailer.java:58)","stack":["java.base/java.net.Socket.connect(Socket.java:633)","com.example.orders.ConfirmationMailer.send(ConfirmationMailer.java:58)"]}
{"kind":"tcp","target":"payments.example.com:443","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.OrderServiceTest.takesPayment","caller":"com.example.orders.PaymentClient.charge(PaymentClient.java:112)","stack":["java.base/sun.nio.ch.SocketChannelImpl.connect(SocketChannelImpl.java:827)","com.example.orders.PaymentClient.charge(PaymentClient.java:112)"]}
{"kind":"process","target":"sendmail","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.NightlyReportJobTest.runsTheJob","caller":"com.example.orders.NightlyReportJob.mailReport(NightlyReportJob.java:77)","stack":["java.base/java.lang.ProcessBuilder.start(ProcessBuilder.java:1089)","com.example.orders.NightlyReportJob.mailReport(NightlyReportJob.java:77)"]}
```

Read line by line, that's a to-do list: the order repository needs a database stand-in, the confirmation mailer a
local mail server, the payment client a WireMock, and the nightly job shouldn't shell out to `sendmail` in a test at
all. Each line names the test that was running and the exact line of the service's own code that made the call.

## Why "airgap", and what it doesn't promise

An air gap usually means a machine with no network connection at all. The airgap agent is narrower, and it's worth
being plain about how. It works inside the JVM it guards, so code written on purpose to get past it (with native code,
or tricks with the JDK's internals) could. It's built for the real risk in an organisation's own old code: ordinary
calls made by accident, because a configuration file says so. Where code can't be trusted that far, real network
separation is still the answer, such as a first run on CI runners that can't reach production. The reasoning is in
[The airgap agent guards against accidental calls from old code, not against hostile code](docs/decisions/0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md).

## Why it can be trusted

A tool like this fails in the worst possible way: silently. If a JDK update changed an internal method and a hook
stopped matching, calls would go through and nothing would look wrong. So:

- at start, the agent checks that every hook it expects for the running JDK is in place, and refuses to run if not;
- every hook is proved on every supported JDK;
- every kind of call is made through every common route, from plain sockets to Netty and JDBC drivers;
- each test checks the refusal came at once, before a connection could even have been attempted.

## Where to go next

- [The design](docs/design.md) covers every risky call and where it's caught, the report, the allowlist, Spring, other
  agents, approval inside a company, testing and the limits.
- [The decision records](docs/decisions/README.md) explain why it's built and not adopted, the promise it makes, how a
  refused call behaves, how its parts fit, and how it's tested.
- [GLOSSARY.md](GLOSSARY.md) explains the words used here.
- To work on the agent itself, start with [AGENTS.md](AGENTS.md).

## Licence

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
