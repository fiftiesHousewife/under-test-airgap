---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# Behind an HTTP proxy, the JDK's own HTTP clients are judged by their real destination as well as the proxy

**Decided by:** Airgap maintainers · **Status:** accepted

## Context and Problem Statement

When code reaches the web through a proxy (the `http.proxyHost` and `https.proxyHost` settings, a `ProxySelector`, or
a client's own proxy setting), the JVM connects only to the proxy, and the proxy connects onwards. The agent's hooks
see one TCP connect, to the proxy, and the destination's name is never looked up on the machine. A proxy that isn't
allowed is refused, so nothing gets out. Once a proxy is allowed, as it must be for a build JVM whose package
repository is reached through the company's proxy, every destination behind it goes ahead unseen.

## Considered Options

* **Judge both, for the JDK's own HTTP clients**  
  `HttpURLConnection` and, from JDK 11, `java.net.http.HttpClient` know the destination when they pick a proxy. New
  hooks there check and report the destination too: a call goes ahead only when the proxy and the destination are
  both allowed. Other clients (OkHttp, Apache HttpClient, Netty) still show only the proxy, and the design says so.
  Good: the common route, and the one a build's own downloads take, is judged by where it really goes.
  Bad: more hooks on JDK internals, each needing its tests on every JDK range; other clients stay a documented limit.
* **The proxy is the target**  
  Keep today's behaviour and document it: the report names the proxy, and allowing a proxy allows everything behind it.
  Good: no new hooks.
  Bad: an allowed proxy is a way round every other rule, in exactly the JVMs that need one.
* **Refuse every proxy**  
  Never allow a connect to a configured proxy.
  Good: simple and safe.
  Bad: a build JVM that can reach its package repository only through a proxy can't run under the agent at all.

## Decision Outcome

Chosen by the airgap maintainers in discussion: judge both, for the JDK's own HTTP clients.

### Consequences

* Good: allowing the company's proxy no longer allows everything behind it, for the JDK's clients.
* Bad: new hooks to keep, prove and add to the JDK route check; the report needs a line that names both the proxy and
  the destination.
* Other clients behind an allowed proxy are listed in the design's limits until they are covered.
* Work: the backlog item for the hooks, with tests through a proxy on the machine itself.

## More Information

* Builds on: [The airgap agent guards against accidental calls from old code, not against hostile code](0002-the-airgap-agent-guards-against-accidental-calls-from-old-code-not-against-hostile-code.md),
  [Every hook is proved on every supported JDK, and the agent checks its own hooks at start](0008-every-hook-is-proved-on-every-supported-jdk-and-the-agent-checks-its-own-hooks-at-start.md)
* Later decisions that build on this: none yet
* Background: [the design: its limits](../design.md#its-limits)
