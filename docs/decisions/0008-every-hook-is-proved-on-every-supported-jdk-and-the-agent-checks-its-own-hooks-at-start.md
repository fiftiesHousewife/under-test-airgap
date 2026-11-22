---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# Every hook is proved on every supported JDK, and the agent checks its own hooks at start

**Decided by:** Airgap maintainers · **Status:** accepted (chosen in discussion)

## Context and Problem Statement

The agent's whole value is a promise: no ordinary call gets past it. Its hooks reach into JDK internals that change
between versions, so a JDK update can make a hook silently stop matching, and the agent would look as if it worked
while letting calls through. That failure is invisible unless the tests are built to catch it.

## Considered Options

* **Prove every hook on every supported JDK, check at start that every expected hook installed, and prove the tests
  catch bugs with mutation testing**
  A broken hook fails the build, or refuses to start, instead of failing silently
* **Unit tests on the build's own JDK only**
  Quick, but misses exactly the changes that break hooks
* **Rely on the end-to-end example repos only**
  Realistic, but a gap in one hook can hide behind another that happens to catch the call

## Decision Outcome

Chosen: the first option, made of six kinds of test:

1. **One test per hook, per JDK** (8, 11, 17, 21 and 25, through Gradle's toolchains): it makes the call and checks it
   was refused with the right exception, at once, and reported correctly. A hook without a test fails the build.
2. **A route test per kind of call:** the same call made through every common route (plain sockets, NIO, the JDK's
   HTTP client, Apache HttpClient, OkHttp, Netty, JDBC drivers, JNDI), so no library finds a way round.
3. **Nothing leaves the machine:** each test aims at an address reserved for documentation (`192.0.2.1`) or a name that
   can't resolve, and checks the refusal came before any connection timeout could.
4. **The agent checks itself at start:** it lists every hook it expected for this JDK and every one it installed, and
   refuses to start, naming the missing hook, if they differ.
5. **Approval tests on old code:** example old services (Spring with Gradle, Spring with Maven, plain Java) run end to
   end, and their reports must match saved, approved reports.
6. **Mutation testing at a high bar:** PIT must kill nearly every planted bug in the agent's refusal and reporting code,
   because a bug there means a call gets through.

### Consequences

* Good: a JDK change that breaks a hook fails this repo's build, and a user's JDK the agent can't fully guard stops the
  run with a clear message rather than guarding it partly.
* Good: the agent's own tests are proven to catch bugs, the standard it helps other code reach.
* Bad: the build needs five JDKs and several third-party clients, so it's slower; CI runs the full matrix, and a
  machine can run its own JDK alone.
* Bad: every new JDK release needs its hooks checked before the agent claims to support it.

## More Information

* Builds on: [It runs on JDK 8 and later, needing no administrator rights or containers](0005-it-runs-on-jdk-8-and-later-needing-no-administrator-rights-or-containers.md)
* Later decisions that build on this: none yet
* Background: [the design: testing it](../design.md#testing-it)
