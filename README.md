# under-test-airgap

[![build](https://img.shields.io/github/actions/workflow/status/fiftiesHousewife/under-test-airgap/build.yml?branch=main&label=build)](https://github.com/fiftiesHousewife/under-test-airgap/actions/workflows/build.yml)
[![maven central](https://img.shields.io/maven-central/v/io.github.fiftieshousewife.airgap/airgap-agent?label=maven%20central)](https://central.sonatype.com/artifact/io.github.fiftieshousewife.airgap/airgap-agent)
[![runs on: Java 8+](https://img.shields.io/badge/runs%20on-Java%208%2B-blue)](#what-the-airgap-agent-does)
[![licence: Apache-2.0](https://img.shields.io/badge/licence-Apache--2.0-blue)](LICENSE)
[![decisions: MADR](https://img.shields.io/badge/decisions-MADR-informational)](docs/decisions/README.md)

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
| Host lookups, and reverse lookups of an address's name | Opening files on shared drives: `\\server\share` paths and mapped network drives |
| TCP connections, through sockets, NIO channels and asynchronous channels | Opening a browser or mail program, and printing |
| UDP traffic | For Spring applications, the setting, file and line that gave each address |
| Starting other programs | Database, HTTP and mail calls named as such in the report |
| JNDI lookups of LDAP, RMI, DNS and CORBA directories on other machines | |
| Loading native libraries, other than the JDK's and the build tool's own | |
| `System.exit` and `Runtime.halt` while a test is running, so the test fails instead of the run ending | |
| Writing, deleting, renaming or moving files, and making folders, outside the project: reported, and refused if the settings say so | |

[The design](docs/design.md) describes each of these.

## Getting started

You need a Gradle build (Gradle 5.6 or later) or a Maven build (Maven 3.6 or later) whose tests you want to run. For
Maven, see [With Maven](#with-maven).

### 1. Add the plugin and run the tests

In the project's `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.fiftieshousewife.airgap") version "..." // the version your company approved
}

airgap {
    packages.add("com.example.")       // your code's packages, so the report names your line of code
}
```

Then run `./gradlew test` as usual.

Gradle finds the plugin on the Gradle Plugin Portal, where the `plugins` block looks by default. The plugin is also on
Maven Central. If your builds can reach only Maven Central, or only a company repository that copies it, name that
repository in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral() // or maven { url = uri("<your company's repository URL>") }
    }
}
```

The plugin fetches the agent and the JUnit listener through the project's own `repositories`, so that list needs
`mavenCentral()` or a company copy of it.

To try the agent without editing the build, use [`airgap.init.gradle`](tools/try-it/airgap.init.gradle) instead.
It adds the agent to the build's tests for one run: `./gradlew test --init-script /path/to/airgap.init.gradle`. The
script fetches the agent from Maven Central. To fetch it from your company's repository instead, add
`-Dairgap.repository=<repository URL>`. To use a jar you already have, add `-Dairgap.agent=<path to the jar>`. The
script can't name your code's packages, so list them in `testing/airgap.yaml` (`packages: [com.example.]`), or the
report may name a library's line instead of yours.

#### With Maven

The Maven extension adds the agent to every module's Surefire, Failsafe, `spring-boot:run` and `exec:exec` runs, and
the JUnit listener to its tests, without changing any pom. It keeps whatever `argLine` a module already sets, such as
JaCoCo's. To keep it for good, name it in `.mvn/extensions.xml`:

```xml
<extensions>
  <extension>
    <groupId>io.github.fiftieshousewife.airgap</groupId>
    <artifactId>airgap-maven-extension</artifactId>
    <version>...</version> <!-- the version your company approved -->
  </extension>
</extensions>
```

To try it for one run, use [`airgap-mvn.sh`](tools/try-it/airgap-mvn.sh) in place of `mvn`, from the project's
folder: `/path/to/airgap-mvn.sh test`. It fetches the three jars with Maven, then runs the project's `./mvnw` (or
`mvn`) with them named in `-Dmaven.ext.class.path`. Set `AIRGAP_REPOSITORY=<repository URL>` to fetch from your
company's repository too. List your code's packages in `testing/airgap.yaml`, as for the init script.

Each run's report goes to `target/airgap/<plugin>-<execution>/calls.jsonl`, for example
`target/airgap/surefire-default-test/calls.jsonl`, and Maven names each report with calls in it as the build ends.
Tests that Maven runs in its own JVM (`forkCount=0`) and `exec:java` can't get the agent this way; the extension warns
about each. For those, run Maven itself with the agent, as below.

### 2. Read the report

Each test task's report is written to `build/airgap/<task name>/calls.jsonl`, for example
`build/airgap/test/calls.jsonl`, and is cleared when the task starts, with the plugin or the init script. If there is
no report, the tests made no risky calls.

Beside it, `calls-summary.json` lists each system the code tried to reach once, with how many calls there were, how
they ended, and which tests and lines of your code made them
([the summary's schema](schema/airgap-summary.schema.json)). Start from it: it is the list of everything your code
depends on. It is rebuilt as each test JVM ends, so it covers every JVM that wrote the report.

A test that made a refused call fails with an ordinary exception:

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

  On a class, it covers the class's set-up and tear-down too (`@BeforeAll`, the constructor, Spring's start-up), and
  applies to subclasses and to `@Nested` classes inside it. On a method, it adds to what the class allows.

- **for the whole project**, list it in `testing/airgap.yaml`, with what it is, so whoever reviews the file knows why
  it's allowed ([the file's schema](schema/airgap-settings.schema.json)):

  ```yaml
  allow:
    - target: wiremock.test:8089
      reason: WireMock standing in for the payment service
    - target: process:git
      reason: The release notes test reads the project's history
  ```

  The plugin hands the file to the agent, and the agent also finds it on its own in the folder a JVM starts in. A
  file that's wrong stops the run at start, saying what's wrong and on which line.

- **for one run**, use the agent's `allow` setting (see [Settings](#settings)), for example
  `allow=wiremock.test:8089,process:git`. It adds to the file's entries.

Each entry is a host (`wiremock.test`), a host and port (`wiremock.test:8089`), a program by its file name or path
(`process:git`), or a native library (`native:sqlitejdbc`, which matches `libsqlitejdbc.so` and `sqlitejdbc.dll`
too). Programs and libraries may use `*` for any run of characters: `process:redis-server-*` allows the Redis server
embedded-redis unpacks. Nothing allows a test to end the JVM.

Some libraries unpack native code of their own to a temporary file, with a version or random id in its name:

| Library | Refused, the tests show | Entry |
|---|---|---|
| SQLite's JDBC driver | tests that use the database fail | `native:*sqlitejdbc` |
| Conscrypt, used by WireMock's web server for TLS | `No Server ALPNProcessors` | `native:*conscrypt*` |
| Netty's native transports and DNS resolver | nothing: Netty carries on without them | `native:netty_*` if you want them |

Writes outside the project are reported but go ahead, unless the file says `file_writes: refuse`. Inside the project
means the build's root folder, the temporary folder and any folders the file lists:

```yaml
file_writes: refuse
write_folders:
  - folder: ~/.m2/repository
    reason: The tests install the library they build
```

### Applications

The plugin also starts every `JavaExec` task with the agent, including Spring Boot's `bootRun`; its report goes to
`build/airgap/<task name>/calls.jsonl` too. The `airgap` block's `allow` adds anything besides `localhost` that every
run may reach, such as `allow.add("wiremock.test:8089")`.

### Without the plugin

A Java agent can't be an ordinary dependency. On the class path it does nothing, because the JVM starts an agent only
when the agent's jar is named on its command line with `-javaagent:`, before any other code runs. The plugin does that
for every task, and names the jar with `-Xbootclasspath/a:` too: without it, HotSpot 17 and later print a warning about
class-data sharing in every JVM. For tests it also lets the JVM attach agents to itself, so Mockito's inline mock maker
doesn't start a second `java` program, which the agent would refuse. Doing the same by hand, for the `test` task:

```kotlin
val airgapAgent = configurations.create("airgapAgent")

dependencies {
    airgapAgent("io.github.fiftieshousewife.airgap:airgap-agent:...")
    testImplementation("io.github.fiftieshousewife.airgap:airgap-junit:...") // for @AllowCalls and test names
}

tasks.test {
    val agent = files(airgapAgent)
    val report = layout.buildDirectory.file("airgap/test/calls.jsonl")
    inputs.files(agent) // a new agent runs the tests again
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Xbootclasspath/a:${agent.singleFile}",
            "-javaagent:${agent.singleFile}=report=${report.get().asFile};project=com.example.",
            "-Djdk.attach.allowAttachSelf=true", // so Mockito's inline mock maker attaches without starting java
        )
    })
}
```

Outside a build, name the agent on the `java` command:

```bash
java -Xbootclasspath/a:airgap-agent.jar -javaagent:airgap-agent.jar="report=build/airgap/calls.jsonl;project=com.example." -jar old-service.jar
```

### Running the build itself with the agent

An old project's build scripts are code too, and they run before its tests. To run Gradle itself with the agent, add
it to `org.gradle.jvmargs` in your own `gradle.properties`, in the Gradle user home (`~/.gradle/gradle.properties`, or
`%USERPROFILE%\.gradle\gradle.properties` on Windows). That file takes precedence over the project's own. Allow your
package repository, so dependencies can download, and `java`, so Gradle can start its test workers:

```properties
org.gradle.jvmargs=-Xmx2g -Xbootclasspath/a:/path/to/airgap-agent.jar -javaagent:/path/to/airgap-agent.jar=run=build;report=/path/to/build-calls.jsonl;allow=repo.example.com:443,process:java
```

`org.gradle.jvmargs` replaces Gradle's default memory setting, so keep an `-Xmx`. Gradle reuses running daemons, so
stop them with `./gradlew --stop` after changing the file.

For Maven, put the same arguments in `MAVEN_OPTS`, or in the project's `.mvn/jvm.config` for every run, with
`process:java` allowed so Surefire can start its test JVMs.

### Settings

Settings follow `=` after the agent's jar on the command line, separated by `;`:

| Setting | What it does |
|---|---|
| `settings=testing/airgap.yaml` | The project's settings file (the default shown, read if it's there): its `mode` and `allow` list. Settings on the command line win over the file's, and `allow` adds to it |
| `mode=refuse` | Refuse and report every risky call that isn't allowed (the default). `report` lets calls go ahead and reports them. `off` does nothing |
| `report=build/airgap/calls.jsonl` | Where the report is written (the default shown) |
| `allow=wiremock.test:8089,process:git` | What to allow for the whole run: `host`, `host:port`, `process:name` or `native:name`. `localhost` is always allowed |
| `run=test` | The kind of JVM, for the report: `test`, `application` or `build` |
| `project=com.example.` | Package prefixes of your own code, so the report names the line of your code that made each call |
| `root=/path/to/build` | The build's root folder, where writes go ahead unreported (the folder the JVM starts in, by default; the plugin sets it) |

A mistyped setting stops the JVM at start.

## An example report

Two lines from the first run of an old order service's tests. Each line is one call, as JSON
([the report's schema](schema/airgap-report.schema.json)); the stacks are shortened here.

```json
{"kind":"host-lookup","target":"db.orders.example.com","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.OrderServiceTest.placesAnOrder","caller":"com.example.orders.OrderRepository.connect(OrderRepository.java:41)","stack":["java.base/java.net.InetAddress.getByName(InetAddress.java:1301)","com.example.orders.OrderRepository.connect(OrderRepository.java:41)"]}
{"kind":"process","target":"sendmail","outcome":"refused","run":{"kind":"test","java_version":"17.0.12"},"test":"com.example.orders.NightlyReportJobTest.runsTheJob","caller":"com.example.orders.NightlyReportJob.mailReport(NightlyReportJob.java:77)","stack":["java.base/java.lang.ProcessBuilder.start(ProcessBuilder.java:1089)","com.example.orders.NightlyReportJob.mailReport(NightlyReportJob.java:77)"]}
```

Each line names the test that was running and the line of the service's code that made the call. The test is named
on the JUnit Platform (JUnit 5 and later, or JUnit 4 through its Vintage engine) when `airgap-junit` is on the
class path, as the plugin arranges, and for JUnit 3 and 4 tests however they're run.

## What it doesn't catch

The agent runs inside the JVM it checks. It is for code that makes risky calls by accident, for example because a
configuration file names a production server. Code written to get round it, with native code or the JDK's internals,
can do so. For code you can't trust that far, use real network separation, such as CI runners that can't reach
production. See
[The airgap agent guards against accidental calls from old code, not against hostile code](docs/decisions/0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md).

Until shared drives are covered, files on `\\server\share` paths and mapped network drives can be read, and writing
to them is only reported: Windows opens them itself, outside the JVM's network code.

## The check at start

The agent changes a few classes inside the JDK, and a JDK update could change one so the agent's change no longer
fits, letting calls through unseen. So each JVM started with the agent first makes one harmless test call of each
kind (a lookup of `airgap-self-check.invalid`, a start of a program that doesn't exist, and so on), none of which can
reach another machine, and confirms each was caught. If one isn't, the JVM stops before the program starts:

```text
The airgap agent couldn't put its hooks in place for [tcp-socket] on Java 26-ea, so it won't start: this JDK isn't
supported yet. Nothing was run unguarded.
```

## How it is tested

- Each of the agent's changes to the JDK is tested on every supported JDK.
- Each kind of call is tested through the JDK's own routes to it: sockets, NIO and asynchronous channels,
  `HttpURLConnection`, the JDK's HTTP client, JNDI. And through the libraries old code most often uses: Apache
  HttpClient 4 and 5, OkHttp, Netty, and the PostgreSQL and MySQL JDBC drivers.
- A test reads every class in each supported JDK and fails if any public route to a risky call misses the hooks.
- In CI, the tests run again under the operating system's own trace, which must show nothing the agent didn't report.
- Each test checks the call was refused before a connection could start.
- The check at start is tested to fail when one of the agent's changes is left out.
- It is tested alongside JaCoCo, Mockito's inline mock maker and PIT.
- The Gradle plugin runs sample builds on each supported Gradle, and the Maven extension on each supported Maven.
- Before each release, real open-source projects' tests are run under it, on the JDKs they were written for.

## For a security review

The agent, `airgap-junit` and the Gradle plugin are on Maven Central under `io.github.fiftieshousewife.airgap`. Each
jar is signed, and published with its sources and a CycloneDX software bill of materials (classifier `cyclonedx`,
type `json`). The agent's jar holds the agent and one library, Byte Buddy, moved under the agent's own package.
`airgap-junit` and the plugin have no dependencies.

## More information

- [The design](docs/design.md): each risky call and where it's caught, the report, the allowlist, Spring, other
  agents, approval inside a company, testing and limits.
- [The decision records](docs/decisions/README.md): the reasons behind each choice.
- [GLOSSARY.md](GLOSSARY.md): the words used here.
- To work on the agent, start with [AGENTS.md](AGENTS.md).

## Licence

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
