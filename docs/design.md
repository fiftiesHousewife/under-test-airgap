# The airgap agent: design

How the airgap agent works, why it works that way, and where it stops. Words follow [GLOSSARY.md](../GLOSSARY.md);
the decisions behind this design are in [docs/decisions/](decisions/README.md).

## The problem

Old code has to be run before it can be tested, and the first time anyone runs an old service, nobody knows what it
will reach: production databases, live payment services, mail servers, file shares, scripts. Those first runs must not
reach production.

The machines the work happens on make that hard:

- **Windows laptops**, and engineers **never have administrator rights**.
- **No container runtime**.
- **Nothing installed from the internet**; every new tool needs a security review and a place in the company's package
  repository.
- **Old JDKs and old builds:** projects build on JDK 8, 11, 17 and later, with Gradle or Maven, and JUnit 4 or 5.

Pulling the network cable isn't possible, and the usual software equivalents all need something these machines don't
allow: containers, operating-system sandboxes, firewall rules or packet capture.

## What the airgap agent does

It refuses risky calls from inside the JVM, and reports each one. Added to the command line as
`-javaagent:airgap-agent.jar`, it rewrites the JDK's own risky methods as their classes load (with
[Byte Buddy](https://bytebuddy.net), the library Mockito is built on). From then on, every caller, whichever library
it goes through, reaches the agent's check first. The rewritten JDK methods call the agent's classes, so the jar must
be on the bootstrap class path: the plugin names it there with `-Xbootclasspath/a:` as well, since a jar the agent adds
there itself makes HotSpot 17 and later print a class-data sharing warning in every JVM.

- **Allowed** (on the allowlist): the call goes ahead, and is recorded.
- **Otherwise:** the call is refused with the exception an unplugged machine would give, and recorded with the test
  that was running and the code that made the call. For Spring applications, the setting that supplied the address
  is planned (see [Spring applications](#spring-applications)).

This document marks what isn't built yet as **planned**; everything else is in the agent today.

The report is the point as much as the refusal. A project's first run under the agent lists everything its code tries
to reach: the start of a list of everything it depends on.

## What it guards against, and what it doesn't

**It guards against accidental calls from old business code:** a test that opens the production database because
that's what `application.yml` says, a start-up that fetches its configuration from a live server, a batch job that
sends email. That code isn't trying to get past anything; it calls the JDK's ordinary APIs.

**It doesn't guard against hostile code.** Anything running inside a JVM can, with enough effort, get past code in the
same JVM: native code, deliberate tricks with the JDK's internals. Where code can't be trusted that far, it needs real
network separation, such as CI runners that can't reach production, and its secrets scanned before it runs at all. See
[The airgap agent guards against accidental calls from old code, not against hostile code](decisions/0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md).

## Options considered

Every way found of running code with no route out, and of recording what it tries, judged against the machines
described in [The problem](#the-problem): Windows, no administrator rights, no containers, nothing installed without
review, JDK 8 and later. The decision is in
[The airgap agent is built rather than adopted, because no maintained tool does the job](decisions/0001-the-airgap-agent-is-built-rather-than-adopted-because-no-maintained-tool-does-the-job.md).
Numbers in square brackets point to [References](#references).

### Separating the machine from the network

| Option | What it does | In a corporate environment |
|---|---|---|
| Pulling the network cable | Real separation | **Rejected:** laptops have no cable to pull, and Wi-Fi and VPN are managed centrally |
| A container with no network (`docker` or `podman run --network none`), or an internal network whose only route is a logging proxy and DNS server | Real separation for one process tree; the proxy and DNS logs show every name tried [1] | **Rejected on laptops:** no container runtime. A good pattern where containers exist |
| CI runners whose outbound traffic is blocked except to the package repository | Real separation, run by the platform team | **Used, but only in CI:** the right home for a project's very first run; doesn't help an engineer's own machine |
| Kubernetes network policies that refuse all outbound traffic, with audit logging (on OpenShift, OVN-Kubernetes ACL logging) | Real separation, with a log line for every dropped connection and the policy that dropped it [2] | **Used, but only in CI**, where CI runs on Kubernetes; no help locally |
| An outbound proxy that refuses and logs everything not on an allowlist, with the process's own network namespace (the design of Anthropic's sandbox-runtime) | Separation by namespace, and a log of every refused name [3] | **Rejected:** needs bubblewrap on Linux or `sandbox-exec` on macOS; nothing equivalent for a plain Windows user. The tool itself is a research preview with a past enforcement bug [4] |
| Pointing the JVM's proxy settings (`http.proxyHost`, `https.proxyHost`) at nothing | Breaks calls made through `URLConnection` and clients that honour those settings | **Rejected:** raw sockets, JDBC drivers, NIO clients and anything that ignores the settings go straight past [5] |

### Sandboxing the process with the operating system

| Option | What it does | In a corporate environment |
|---|---|---|
| macOS `sandbox-exec` with a profile that refuses outbound network | Refuses the process and its children; denials appear in the system log [6] | **Rejected as the answer:** macOS only, and deprecated by Apple, though it still works. Most corporate laptops run Windows |
| Linux Landlock (network rules from kernel 6.7), for example through landrun | Unprivileged refusal of TCP connections for a process and its children [7] [8] | **Rejected as the answer:** Linux only, TCP only, and needs a recent kernel |
| Linux bubblewrap with its own network namespace | Unprivileged separation, where user namespaces are allowed | **Rejected as the answer:** Linux only; user namespaces are often switched off on managed machines |
| Windows AppContainer, through tools such as landstrip or rappct | Runs a process with no network capability [9] [10] | **Rejected:** young tools, each needing its own security review; behaviour with loopback and name lookups is awkward |
| Windows Sandbox | A throwaway virtual machine with networking switched off [11] | **Rejected:** a Windows feature that needs enabling by an administrator, and a whole virtual machine per run |
| Windows Defender Firewall rules per program | Refuses a program's outbound traffic | **Rejected:** needs administrator rights |

### Watching the traffic

| Option | What it does | In a corporate environment |
|---|---|---|
| Packet capture (Wireshark, tcpdump) | Records every packet on the machine | **Rejected:** needs elevated rights and a capture driver, sees other people's and other programs' traffic, and records rather than refuses |
| eBPF monitors (Falco, Tetragon) | Watch and refuse system calls in the kernel | **Rejected:** need root on a Linux host; nothing for a Windows laptop |
| Recording proxies (WireMock's record mode, Hoverfly's capture mode) | Record HTTP traffic and replay it later | **Not for safety:** they only see traffic sent to them, and only HTTP. Useful later, to turn the calls the agent finds into recorded responses [12] |

### Refusing inside the JVM

| Option | What it does | In a corporate environment |
|---|---|---|
| The JDK's Security Manager with a policy refusing socket permissions | Refused network, file and process access by policy, across the whole JVM [13] | **Rejected:** deprecated from JDK 17 and disabled from JDK 24; OpenJDK provides no replacement and points to operating-system sandboxing [14] [15] |
| A custom socket factory (`Socket.setSocketImplFactory`) | Replaces how plain sockets are made | **Rejected:** plain sockets only, not NIO channels, which Netty, newer HTTP clients and several JDBC drivers use; deprecated [5] |
| The JDK's address-resolution hook (JEP 418) | Lets a provider answer, log or refuse every host lookup [16] | **Rejected as the answer:** JDK 18 and later only, and connections straight to an IP address skip lookups. Kept as a possible extra |
| The JDK's own connection events in JDK Flight Recorder | Records socket connects, with host, port and stack trace | **Rejected as the answer:** records but doesn't refuse; only on recent JDKs; very fast connects can fall under its recording threshold [17] |
| A JUnit extension on its own (JUnit 5 or 6) | Knows which test is running; can install the hooks above | **Rejected as the answer:** sees only tests, not the application or the build; limited to the weak hooks above; JUnit 6 needs Java 17. Kept as the part that reports and allows |
| The JUnit extension loading an agent itself when tests start, as Mockito does | No command-line change | **Rejected:** JDK 21 and later warn about agents loaded this way, and plan to refuse them by default [18] |
| [junit-airgap](https://klibs.io/project/garry-jeromson/junit-airgap) | Fails JUnit 4 and 5 tests that use the network, through a native agent [19] | **Rejected:** a single-author beta; a native library to approve for each operating system; tests only |
| [Sniffy](https://github.com/sniffy/sniffy) | Refuses new socket connections in tests | **Rejected:** a JUnit 4 rule; no recent work found [20] |
| Byteman scripts | Rewrite methods by rule, for example to trace every `Socket.connect` [21] | **Rejected as the answer:** a general tool needing its own rules for every JDK range; the airgap agent's hooks are the same idea, built in |
| BlockHound | An agent that refuses blocking calls in reactive code [22] | **Not for this job,** but the same technique, and a model for allowing named exceptions |
| [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer)'s sanitizers | Hooks risky JVM calls (network, command execution, JNDI, SQL) to find bugs while fuzzing [23] | **Rejected:** a fuzzing tool, not a way to run code offline. Its list of hooks informs ours |
| [OpenRASP](https://github.com/baidu/openrasp) | A runtime protection agent that hooks process starts, outbound URLs and file access, with log and block modes [24] | **Rejected:** built to stop attacks on live servers; heavy; no release since 2022. Its list of hooks informs ours |
| A Byte Buddy agent in place of the Security Manager | Applies the same rules by rewriting bytecode with Byte Buddy's `AgentBuilder` and `Advice` [25] [26] | **The approach chosen.** Kestra's agent is part of Kestra, not a library, so the airgap agent builds the same approach for this job |

### In other languages

Ruby's WebMock (`disable_net_connect!`), Python's pytest-socket and Node's nock (`disableNetConnect`) give "no network
in tests" in one line. The JVM has nothing maintained that does the same [5].

### References

1. Docker documentation: none network driver. <https://docs.docker.com/engine/network/drivers/none/>
2. Red Hat OpenShift documentation: audit logging for network security. <https://docs.redhat.com/en/documentation/openshift_container_platform/4.21/observability/network_security/logging-network-security>
3. Anthropic sandbox-runtime. <https://github.com/anthropic-experimental/sandbox-runtime>
4. GitLab advisory: sandbox-runtime network sandboxing. <https://advisories.gitlab.com/pkg/npm/@anthropic-ai/sandbox-runtime/>
5. "Is there a way to block all external network access from a Java JUnit test?" (Stack Overflow). <https://stackoverflow.com/questions/67099279>
6. Running code in a macOS sandbox. <https://lucaswiman.github.io/2023/06/04/macos-sandbox.html>
7. Linux kernel documentation: Landlock (6.7). <https://docs.kernel.org/6.7/userspace-api/landlock.html>
8. landrun. <https://github.com/Zouuup/landrun>
9. landstrip (Windows AppContainer). <https://docs.rs/crate/landstrip/0.9.0/source/src/windows.rs>
10. rappct (Windows AppContainer toolkit). <https://docs.rs/crate/rappct/latest/source/examples/network_demo.rs>
11. Microsoft: Windows Sandbox configuration. <https://learn.microsoft.com/en-us/windows/security/application-security/application-isolation/windows-sandbox/windows-sandbox-configure-using-wsb-file>
12. WireMock: record and playback. <https://wiremock.org/docs/record-playback/>
13. Oracle: Java SE platform security architecture (JDK 17). <https://docs.oracle.com/en/java/javase/17/security/java-se-platform-security-architecture.html>
14. JEP 411: Deprecate the Security Manager for Removal. <https://openjdk.org/jeps/411>
15. JEP 486: Permanently Disable the Security Manager. <https://openjdk.org/jeps/486>
16. JEP 418: Internet-Address Resolution SPI. <https://openjdk.org/jeps/418>
17. OpenJDK review: add JFR event for connect operations. <https://mail.openjdk.org/archives/list/hotspot-jfr-dev@openjdk.org/thread/T7FYH5RBQYFK4WNGCDEKYUJ5AVKAQI3R>
18. JEP 451: Prepare to Disallow the Dynamic Loading of Agents. <https://openjdk.org/jeps/451>
19. junit-airgap. <https://klibs.io/project/garry-jeromson/junit-airgap>
20. Sniffy. <https://github.com/sniffy/sniffy>
21. Byteman sample script: SocketMonitor. <https://sources.debian.org/src/byteman/4.0.20-1/sample/scripts/SocketMonitor.btm>
22. BlockHound. <https://github.com/reactor/BlockHound>
23. Jazzer. <https://github.com/CodeIntelligenceTesting/jazzer>
24. OpenRASP JavaScript plugin development. <https://github.com/baidu/openrasp/wiki/JavaScript-plugin-development>
25. Kestra: replacing Java's SecurityManager with a Byte Buddy agent. <https://medium.com/kestra-engineering/replacing-javas-securitymanager-with-a-byte-buddy-agent-539f218c3eb5>
26. Loïc Mathieu: replacing Java's SecurityManager with an agent using Byte Buddy. <https://www.loicmathieu.fr/wordpress/informatique/replacing-javas-securitymanager-with-an-agent-using-byte-buddy/>

Two of these weren't checked directly. The Docker and WireMock pages are cited from their projects' documentation, not
from this search. The JDK Flight Recorder connection events are known only from OpenJDK review threads; whether they
ship in a given JDK still needs confirming, with that JDK's `jfr metadata`.

## Where it runs

The same agent jar is added to every JVM that runs the project's code:

| JVM | Why | How it's added |
|---|---|---|
| Test runs | Tests are where old code first runs | Gradle: every `Test` task. Maven (planned): Surefire's and Failsafe's `argLine` |
| Application starts | Starting the application on its own is the next thing anyone tries | Gradle: every `JavaExec` task, and Spring Boot's `bootRun`. Maven (planned): the plugin's `jvmArguments` |
| The build itself | An old project's build scripts are its code too, and run first | Gradle: `org.gradle.jvmargs` in the engineer's own `gradle.properties` (in the Gradle user home, which wins over the project's). Maven: `MAVEN_OPTS` |
| Forked JVMs | PIT and some test set-ups start more JVMs | PIT's `jvmArgs`, set by hand. A JVM started with `java` from a guarded JVM is refused unless `process:java` is allowed, and then runs unguarded unless it is named the agent too; adding the agent to it automatically is planned |

In the build JVM, the `allow` setting names the company's package repository, so dependencies can download, and
`process:java`, so Gradle can start its workers; nothing else.

For Gradle, the plugin `io.github.fiftieshousewife.airgap` does the first two rows in one line, on Gradle 5.6 and later
(see [The Gradle wiring supports Gradle 5.6 and later, proved on each major version](decisions/0010-the-gradle-wiring-supports-gradle-5-6-and-later-proved-on-each-major-version.md)).
Each task writes its own report, `build/airgap/<task name>/calls.jsonl`, cleared when the task starts. The build JVM
is set up by hand, as the README describes, because a plugin is applied too late to guard the build that applies it.

## What counts as a risky call, and where it's caught

The agent catches each call at the lowest point in the JDK that every route goes through, not at the public APIs, so
no library is missed. Those low points differ between JDK versions: each hook names the methods it rewrites by what
they are (the private `getAllByName0` with the most parameters, say) rather than by one JDK's exact signature, and a
hook for a class only some JDKs have (`SocketChannel`'s handed-out socket from JDK 13, the foreign function API from
JDK 22) applies only where that class is. Each is proved by tests on every supported JDK (see
[Testing it](#testing-it)).

| Kind | Caught at (JDK internals vary by version) | Refused with |
|---|---|---|
| Host lookup | The JDK's own forward lookup above its cache (the private `getAllByName0`), which every public lookup and the JDK's own name checks (in `SocketPermission`, security policies and RMI) reach; the machine looking up its own name (`getLocalHost`) goes ahead. Also reverse lookups (`getHostName`, `getCanonicalHostName`). A name counts as written as an address only if it is a real one, and as the machine itself only if it is a real loopback address or `localhost` | `UnknownHostException`; a refused reverse lookup gives the address back as its name, as offline |
| TCP connection | `Socket`'s connect, `SocketChannel` connects, and from JDK 13 the socket a `SocketChannel` hands out, whose connect goes round both; `InetAddress.isReachable`, which sends echo requests; SCTP connects and multi-association sends, where the JDK supports SCTP | `ConnectException`; a refused `isReachable` answers `false` |
| UDP | `DatagramSocket` and `DatagramChannel` send and connect, and `MulticastSocket`'s send with a time-to-live (also covers DNS done by JNDI) | `PortUnreachableException`; wrapped in `UncheckedIOException` by `DatagramSocket.connect(InetAddress, int)`, which declares no checked exception, as the JDK does from 14; `SocketException` for an address nobody looked up, as the JDK refuses one |
| Asynchronous channels | `AsynchronousSocketChannel` connects | a failed future, or the handler's `failed` called, with `ConnectException` |
| JNDI and LDAP | `InitialContext`'s names: URLs with the `ldap:`, `ldaps:`, `rmi:`, `dns:` and CORBA schemes, and the directory a context's settings name as its provider URL. Names inside the JVM (`java:comp/env`) are left alone; the connections JNDI providers make are caught by the network hooks too | `CommunicationException`, the `NamingException` an unreachable directory gives |
| Starting a process | `ProcessBuilder.start` (which `Runtime.exec` uses), and from JDK 9 `ProcessBuilder.startPipeline` | `IOException` ("cannot run program") |
| Loading native code | `System.loadLibrary`, `System.load` and their `Runtime` versions, called directly or through reflection, and JDK 22's `SymbolLookup.libraryLookup` (which fails with `IllegalArgumentException`), except the JDK's own libraries and the build tool's own (see [Native libraries are refused unless allowed, except the JDK's and the build tool's own](decisions/0012-native-libraries-are-refused-unless-allowed-except-the-jdks-and-the-build-tools-own.md)). A library is the JDK's only when a JDK class loader can see the class asking for it, so a class path library in a JDK-like package, such as JNA's `com.sun.jna`, is not (proposed in [A native library is the JDK's own only when a JDK class loader can see the class asking for it](decisions/0013-a-native-library-is-the-jdks-own-only-when-a-jdk-class-loader-can-see-the-class-asking-for-it.md)) | `UnsatisfiedLinkError` |
| Ending the JVM | `System.exit` (through `Runtime.exit`) and `Runtime.halt`, only while a test or a test class's set-up is running on the thread, so test runners end their JVMs as usual (see [An exit is refused only while a test is running](decisions/0011-an-exit-is-refused-only-while-a-test-is-running.md)) | `SecurityException` naming the airgap agent |
| Writing files outside the project (planned) | `FileOutputStream`, `RandomAccessFile` and `Files` writes | Reported only, at first (see [Open questions](#open-questions)) |

**Planned:** higher-level calls recognised too, only to describe them better in the report: a JDBC connection saying
`kind: database` with its URL, an HTTP client call `kind: http` with its address, a mail transport `kind: mail`.

Local calls are always allowed: loopback addresses and `localhost`, connections and packets to the machine's own
network addresses (which RMI and JMX hand out for objects in the same JVM), and the machine looking up its own name.
Looking up the name of one of its network addresses isn't local: that name comes from DNS.

## How a call is refused

As an unplugged machine would refuse it: the same exception type, thrown at once, with a message that names the
airgap agent. The code under test then takes the path it would take offline, and its tests fail or pass as they would
without a network. A refused call never returns made-up success: a pretend answer would let invented behaviour into
tests that record what the code does today. See
[Refused calls fail as an unplugged machine would, never with made-up success](decisions/0003-refused-calls-fail-as-an-unplugged-machine-would-never-with-made-up-success.md).

Three modes, chosen per run:

| Mode | Refuses | Reports | Used for |
|---|---|---|---|
| `refuse` (the default) | Everything not allowed | Every call | Every run on an engineer's machine |
| `report` | Nothing | Every call | CI runs on runners whose network is already blocked, to name what was tried |
| `off` | Nothing | Nothing | Once a project is known to be safe, if its owners choose |

## The allowlist

What may go ahead, from three places:

1. **Always:** loopback addresses and `localhost`.
2. **The run's `allow` setting,** on the agent's command line, or the Gradle plugin's `allow` list: the stand-ins its
   tests use, for example a WireMock server, and in the build JVM the company's package repository. A project
   settings file, `testing/airgap.yaml` ([schema](../schema/airgap-settings.schema.json)), is planned.
3. **A test's own allowance:** `@AllowCalls("wiremock.test:8089")` on a test class or method.

An allowed call is still reported, so the report always shows everything the code touched.

## The report

`build/airgap/<task name>/calls.jsonl` for each task (`build/airgap/calls.jsonl` when the agent is named by hand): one
JSON object per line, per call, checked against
[schema/airgap-report.schema.json](../schema/airgap-report.schema.json). Each line says:

- **what:** its kind (`host-lookup`, `tcp`, `udp`, `jndi`, `process`, `native-library`, `exit`; `database`, `http`,
  `mail` and `file-write` are planned) and its target (host and port, URL, command or library);
- **what happened:** `refused`, `allowed` or `reported`;
- **who:** the test that was running, and the first frame of the project's own code that made the call, with a
  trimmed stack;
- **where the address came from,** for Spring applications (planned): the setting's name and the file and line it
  was read from;
- **which JVM:** the kind of run (test, application start, build) and its Java version.

No report must mean no risky calls, so the agent won't start if it can't write the report's folder, and a call whose
line can't be written is refused, whatever the mode or allowlist, with a message saying the report failed. A line is
written even from a thread that has been interrupted.

**Planned:** a summary at the end of the run listing each distinct target once, with how many calls and which tests made them: the
list of everything the project depends on, for a person to confirm and give stand-ins. See
[The report is one line of JSON per call, with a schema, and feeds the connection list](decisions/0006-the-report-is-one-line-of-json-per-call-with-a-schema-and-feeds-the-connection-list.md).

## Knowing which test made a call

The JUnit Platform listener, registered automatically through Java's service loader, tells the agent when each test
class and each test starts and ends. A test class's set-up and tear-down (`@BeforeAll`, its constructor, Spring's
start-up, `@AfterAll`) are named by the class, and each test runs inside its class: it starts with what the class's
`@AllowCalls` allows, with those of its base classes and the classes it is nested in, and adds its own method's. The agent keeps the current test per thread, passed on to threads the test starts, which see it, and what it
allows, only while the test runs: a pool that outlives its test is neither allowed what the test was nor blamed for
its calls. Work handed to a shared thread pool loses that link, so every line also names the first frame of the
project's own code, which is enough to find the caller.

Projects on JUnit 4 move onto the JUnit Platform first: JUnit 4 tests keep running, unchanged, on its Vintage engine.
JUnit 6 needs Java 17, so projects on JDK 8 to 16 use JUnit 5. The listener and extension are built against the JUnit 5
API, which JUnit 6 also runs.

## Spring applications

**Planned.** Today a Spring application gets what any Java code gets: the refusal, the report line and the caller.

In old Spring applications, the dangerous addresses usually come from configuration, and the default profile is often
production: real hosts and passwords in a plain `application.properties`, with no profile at all. So the agent will:

- **Name the setting behind each refused address.** Spring Boot keeps the file and line each property came from; at a
  refusal, the agent looks up which property holds the refused address and records its name and origin. The engineer
  then knows exactly what the test settings have to override.
- **Record the profiles Spring activates,** however they were set, and stop the run if one matches the production
  profile names it's given.
- **Point out configuration fetched at start-up** (Spring Cloud Config, Vault, a JNDI data source): a refused call
  before the application has even started, and a dependency the code itself never names. The refusal itself already
  happens today.

Together these will let an engineer write a test profile, one override per refused address with a stand-in or a dummy
secret, until a run's report is clean.

Spring applications without Spring Boot (and Spring before 3.1, which has no profiles) get the refusal and the caller;
the setting is named where the agent can tell which property source held it.

## Living alongside other agents

The JVMs it joins often carry other agents: JaCoCo (coverage), PIT (mutation testing) and Mockito's inline mock maker.
The agent:

- **keeps its own copy of Byte Buddy** under its own package name, so it never clashes with the copy Mockito brings;
- **is added first** on the command line by the plugin, so it sees classes before the others rewrite them;
- runs with JaCoCo in this repo's own build today; **planned:** tests together with PIT and Mockito's inline mock
  maker.

## Approval inside a company

One jar, Apache-2.0, built from source in this repo; its only dependency, Byte Buddy (Apache-2.0), is inside it. No
native code, no network access of its own, and it writes only to the build folder. Each release publishes a software
bill of materials, so a security review has everything in one place.

## Testing it

The agent's value is a promise that no ordinary call gets past it, and its hooks reach into JDK internals that change
between versions. The most dangerous failure is a hook that silently stops matching, so the tests are built to make
that impossible to miss (see
[Every hook is proved on every supported JDK, and the agent checks its own hooks at start](decisions/0008-every-hook-is-proved-on-every-supported-jdk-and-the-agent-checks-its-own-hooks-at-start.md)):

- **Every hook, on every JDK it supports:** the build runs the agent's tests on JDK 8, 11, 17, 21 and 25, through
  Gradle's toolchains. Each hook has a test that makes the call and checks it was refused with the right exception, at
  once, and reported correctly.
- **Every route through the JDK, found by reading it:** on every supported JDK, a test reads all the JDK's own classes,
  builds who calls whom (through overriding methods, lambdas and callbacks too), and walks back from the JDK's last
  steps before each kind of call: its native connects, sends, lookups, process starts, library loads and exits. Every
  public JDK method it reaches without passing a hook must be on a reviewed list saying why it is safe. A new JDK that
  opens a new route fails the build instead of going unnoticed. It found the socket a `SocketChannel` hands out,
  `isReachable`, the JDK's own name checks and the foreign function API, all now hooked.
- **What the operating system saw:** in CI, on Linux, the agent's own tests run again under strace, which records
  every connection and packet, every program started and every library opened, by the test JVMs and every process
  they start. Anything to another machine, a program or a library that the agent didn't report as allowed fails the
  build: a way round the hooks shows up there, whatever route it took, even one nobody thought of.
- **Every route to each kind of call:** plain sockets, NIO channels, asynchronous channels, `HttpURLConnection` and
  JNDI today. **Planned:** the JDK's HTTP client, Apache HttpClient, OkHttp, Netty and JDBC drivers.
- **That nothing leaves the machine:** each test aims at an address reserved for documentation (`192.0.2.1`) or a name
  that can't resolve, and has a timeout well short of a network timeout, so a refusal that didn't happen shows as a
  failure.
- **The agent checks itself at start:** for every hook that applies to the running JDK, it makes a harmless call the
  hook must stop, or, where no call is harmless (ending the JVM, SCTP, the foreign function API), checks the methods
  are there to rewrite. If any hook isn't in place it refuses to start, naming the gap: a JDK it can't fully guard
  stops the run rather than being guarded partly.
- **With the other agents** (planned for PIT and Mockito) in the same JVM.
- **On old code (planned):** example old services (Spring with Gradle, Spring with Maven, plain Java), run end to end, whose
  reports must match saved, approved reports.
- **Mutation testing at a high bar:** PIT must kill at least 90% of the bugs it plants in the code that decides,
  refuses and reports calls, since a bug there means a call gets through.

## Its limits

- **Native code** loaded before the agent, or allowed with `native:`, or code that uses the JDK's internals to go
  round it, isn't caught.
- **An exit outside a test** isn't refused: with no `airgap-junit` on the tests' class path, or from a shared thread
  pool, the agent can't tell the project's exit from the test runner's.
- **Programs other than Java** are refused at the point of starting, but if a start is allowed, the program runs
  unguarded.
- **Shared drives** (`\\server\share` paths and mapped network drives) are opened by the operating system, not
  through the JVM's sockets, so the network hooks don't see them. A planned hook on the JVM's file APIs will refuse
  and report opening any file on a `\\server\share` path, and on drive letters the settings name as network drives
  (the JVM can't tell a mapped drive from a local one without native code). Until then, they can be read and written.
- **Gradle daemons are reused** between builds: the agent goes into the daemon's own settings, so a daemon started
  before it was added has to be stopped first.
- **It's no substitute for a secrets scan, or for network separation** where code can't be trusted; it's the safety net
  for runs on an engineer's own machine.

## Open questions

- **Writing files outside the project:** refuse, or only report? Old batch jobs write to fixed paths such as
  `C:\data\out`; refusing finds them, but breaks runs that would otherwise teach something. Proposed: report only at
  first.
- **Non-Java projects** (Node, shell scripts) aren't covered.
