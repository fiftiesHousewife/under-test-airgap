# under-test-airgap

[![Build](https://github.com/fiftiesHousewife/under-test-airgap/actions/workflows/build.yml/badge.svg)](https://github.com/fiftiesHousewife/under-test-airgap/actions/workflows/build.yml)
[![Licence: Apache-2.0](https://img.shields.io/badge/licence-Apache--2.0-blue)](LICENSE)
![Runs on JDK 8 and later](https://img.shields.io/badge/runs%20on-JDK%208%20and%20later-blue)

**Run old Java code without letting it reach other machines, and get a list of everything it tried to reach.**

## The problem it solves

You are given an old Java service to test, and you have not seen it run. Its tests don't pass. Its
`application.properties` may point at the production database. A scheduled job in it may send email to customers, or
write to a shared drive. You need to run the code to test it, but running it could change real systems.

The usual ways to stop a program reaching the network are containers, operating-system sandboxes, firewall rules and
packet capture. Each one needs administrator rights or software installed, and most corporate laptops allow neither.
Inside the JVM, the Security Manager could refuse connections by policy, but recent JDKs disable it. No maintained
tool does this job for the JVM, as WebMock does for Ruby or pytest-socket for Python.

## What the airgap agent does

The airgap agent is a Java agent: a jar named on the JVM's command line. It needs nothing installed and no rights
beyond running Java, and it works on JDK 8 and every later version.

When the JVM loads its own classes, the agent changes the few places in the JDK where risky calls happen. Every
library goes through those places, from old JDBC drivers to Netty, so every library's calls are checked. Calls to the
same machine go ahead. Calls to any other machine are refused with the exception a machine without a network would
throw (`UnknownHostException`, `ConnectException`), so the code fails the way it would offline. Each refused call is
written to a report.

| Refused and reported now | Coming |
|---|---|
| Host lookups | JNDI and LDAP lookups |
| TCP connections, through sockets, NIO channels and asynchronous channels | Loading native libraries |
| UDP traffic | `System.exit` |
| Starting other programs | Opening files on shared drives: `\\server\share` paths and mapped network drives |
| | Writing files outside the project folder |
| | For Spring applications, the setting, file and line that gave each address |

[The design](docs/design.md) describes each of these.

## Getting started

You need a Gradle build whose tests you want to run.

### 1. Run the tests with the agent

Download [`airgap.init.gradle`](tools/try-it/airgap.init.gradle). It adds the agent to a build's tests without
changing that build or its CI. In the project you want to test, run:

```bash
./gradlew test --init-script /path/to/airgap.init.gradle
```

The script fetches the agent from Maven Central. To fetch it from your company's repository instead, add
`-Dairgap.repository=<repository URL>`. To use a jar you already have, add `-Dairgap.agent=<path to the jar>`.

### 2. Read the report

Each project's report is written to its own `build/airgap/calls.jsonl`. If there is no report, the tests made no
risky calls. A test that made a refused call fails with an ordinary exception:

```text
OrderServiceTest > placesAnOrder() FAILED
    java.net.UnknownHostException: db.orders.example.com: refused by the airgap agent
```

### 3. Decide what to do about each line

Each line in the report is a system the code tried to reach. The agent only finds them: how a test replaces one
(a mock, a fake, an in-memory database, a WireMock server) is up to you and the tools you already use. Run the tests
again after each change, until the report is empty.

Calls to `localhost` are always allowed. To allow anything else, such as a stand-in under its own host name or a
program a test starts on purpose:

- **for one test class or method**, use `@AllowCalls` from `airgap-junit` (JUnit 5 and later), on the class or the
  method:

  ```java
  import io.github.fiftieshousewife.airgap.junit.AllowCalls;

  @AllowCalls("wiremock.test:8089")
  class PaymentClientTest { ... }
  ```

- **for a whole run**, use the agent's `allow` setting (see [Settings](#settings)), for example
  `allow=wiremock.test:8089,process:git`.

Each entry is a host (`wiremock.test`), a host and port (`wiremock.test:8089`), or a program (`process:git`).

### Adding the agent to a build

To run a project's tests with the agent every time, apply the Gradle plugin. It works with Gradle 5.6 and later.

```kotlin
plugins {
    id("io.github.fiftieshousewife.airgap") version "..." // the version your company approved
}

airgap {
    packages.add("com.example.")       // your code's packages, so the report names your line of code
    allow.add("wiremock.test:8089")    // anything besides localhost the tests may reach
}
```

The plugin fetches the agent and the JUnit listener from the project's repositories. It starts every `Test` task and
every `JavaExec` task with the agent, including Spring Boot's `bootRun`. Each task's report goes to
`build/airgap/<task name>/calls.jsonl`, and is cleared when the task starts.

Outside a build, name the agent on the `java` command:

```bash
java -javaagent:airgap-agent.jar="report=build/airgap/calls.jsonl;project=com.example." -jar old-service.jar
```

### Running the build itself with the agent

An old project's build scripts are code too, and they run before its tests. To run Gradle itself with the agent, add
it to `org.gradle.jvmargs` in your own `gradle.properties`, in the Gradle user home (`~/.gradle/gradle.properties`, or
`%USERPROFILE%\.gradle\gradle.properties` on Windows). That file takes precedence over the project's own. Allow your
package repository, so dependencies can download, and `java`, so Gradle can start its test workers:

```properties
org.gradle.jvmargs=-Xmx2g -javaagent:/path/to/airgap-agent.jar=run=build;report=/path/to/build-calls.jsonl;allow=repo.example.com:443,process:java
```

`org.gradle.jvmargs` replaces Gradle's default memory setting, so keep an `-Xmx`. Gradle reuses running daemons, so
stop them with `./gradlew --stop` after changing the file.

### Settings

Settings follow `=` after the agent's jar on the command line, separated by `;`:

| Setting | What it does |
|---|---|
| `mode=refuse` | Refuse and report every risky call that isn't allowed (the default). `report` lets calls go ahead and reports them. `off` does nothing |
| `report=build/airgap/calls.jsonl` | Where the report is written (the default shown) |
| `allow=wiremock.test:8089,process:git` | Addresses and programs to allow for the whole run: `host`, `host:port` or `process:name`. `localhost` is always allowed |
| `run=test` | The kind of JVM, for the report: `test`, `application` or `build` |
| `project=com.example.` | Package prefixes of your own code, so the report names the line of your code that made each call |

A mistyped setting stops the JVM at start.

## An example report

This report comes from the first run of an old order service's tests. Each line is one call, as JSON
([the report's schema](schema/airgap-report.schema.json)). The stack traces are shortened here.

```json
{"kind":"host-lookup","target":"db.orders.example.com","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.OrderServiceTest.placesAnOrder","caller":"com.example.orders.OrderRepository.connect(OrderRepository.java:41)","stack":["java.base/java.net.InetAddress.getByName(InetAddress.java:1301)","com.example.orders.OrderRepository.connect(OrderRepository.java:41)"]}
{"kind":"tcp","target":"10.20.4.17:25","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.OrderServiceTest.emailsTheCustomer","caller":"com.example.orders.ConfirmationMailer.send(ConfirmationMailer.java:58)","stack":["java.base/java.net.Socket.connect(Socket.java:633)","com.example.orders.ConfirmationMailer.send(ConfirmationMailer.java:58)"]}
{"kind":"tcp","target":"payments.example.com:443","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.OrderServiceTest.takesPayment","caller":"com.example.orders.PaymentClient.charge(PaymentClient.java:112)","stack":["java.base/sun.nio.ch.SocketChannelImpl.connect(SocketChannelImpl.java:827)","com.example.orders.PaymentClient.charge(PaymentClient.java:112)"]}
{"kind":"process","target":"sendmail","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.NightlyReportJobTest.runsTheJob","caller":"com.example.orders.NightlyReportJob.mailReport(NightlyReportJob.java:77)","stack":["java.base/java.lang.ProcessBuilder.start(ProcessBuilder.java:1089)","com.example.orders.NightlyReportJob.mailReport(NightlyReportJob.java:77)"]}
```

Each line names the test that was running and the line of the service's code that made the call. This report shows
the service's tests depend on a database, a mail server, a payment service and the `sendmail` program.

## What it doesn't catch

The agent runs inside the JVM it checks. It is for code that makes risky calls by accident, for example because a
configuration file names a production server. Code written to get round it, with native code or the JDK's internals,
can do so. For code you can't trust that far, use real network separation, such as CI runners that can't reach
production. See
[The airgap agent guards against accidental calls from old code, not against hostile code](docs/decisions/0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md).

Until shared drives are covered, files on `\\server\share` paths and mapped network drives can be read and written:
Windows opens them itself, outside the JVM's network code.

## The check at start

The agent works by changing a few classes inside the JDK. Those classes differ between JDK versions, and a JDK update
could change one so that the agent's change no longer fits. Calls would then go ahead with no sign of it.

So each time a JVM starts with the agent, before the program runs, the agent checks itself. For each kind of risky
call it catches, it makes one test call (a host lookup of `airgap-self-check.invalid`, a connection to that name, a
UDP packet to the same machine, a start of a program that doesn't exist) and confirms the agent's change caught it.
None of these test calls can reach another machine, even if the check fails.

If any test call isn't caught, the JVM stops before the program starts, with a message such as:

```text
The airgap agent couldn't put its hooks in place for [tcp-socket] on Java 26-ea, so it won't start: this JDK isn't
supported yet. Nothing was run unguarded.
```

Your tests don't need to do anything for the check.

## How it is tested

- Each of the agent's changes to the JDK is tested on every supported JDK.
- Each kind of call is tested through each common library, from plain sockets to Netty and JDBC drivers.
- Each test checks the call was refused before a connection could start.
- Each test also checks the check at start fails when one of the agent's changes is left out.

## More information

- [The design](docs/design.md): each risky call and where it's caught, the report, the allowlist, Spring, other
  agents, approval inside a company, testing and limits.
- [The decision records](docs/decisions/README.md): the reasons behind each choice.
- [GLOSSARY.md](GLOSSARY.md): the words used here.
- To work on the agent, start with [AGENTS.md](AGENTS.md).

## Licence

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
