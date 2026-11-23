---
status: accepted
decision-makers: Airgap maintainers
group: How it works
---

# A native allow entry matches a library's name exactly, or by wildcards

**Decided by:** Airgap maintainers · **Status:** accepted

## Context and Problem Statement

A `native:` entry allows a native library by name: the file name with its folder, its `lib` prefix and its extension
removed, so `native:sqlitejdbc` matches `libsqlitejdbc.so` and `sqlitejdbc.dll`. Several libraries extract their
native code to temporary files whose names carry versions or random ids: sqlite-jdbc writes files such as
`sqlite-3.45.1.0-<id>-libsqlitejdbc.so`, and Netty `libnetty_transport_native_epoll_x86_64<id>.so`. No entry could
match those.

## Considered Options

* **Exact, or with `*` wildcards**  
  An entry matches the name exactly, or, where it has a `*`, with each `*` standing for any run of characters:
  `native:*sqlitejdbc`, `native:netty_transport_native_epoll*`.
  Good: nothing is allowed that the entry didn't ask for; one rule for every library.
  Bad: people have to know a library's file names; the README lists the common ones.
* **The name anywhere in the file name**  
  `native:sqlitejdbc` matches any file whose name contains `sqlitejdbc`.
  Good: no new syntax.
  Bad: a short entry such as `native:ssl` matches more than meant.
* **Exact names, and a folder entry**  
  Add `native-path:<folder>` to allow anything loaded from a folder.
  Good: covers any name.
  Bad: the folders these libraries use are the shared temporary folder, so an entry allows a great deal.

## Decision Outcome

Chosen by the airgap maintainers in discussion: exact, or with `*` wildcards.

### Consequences

* Good: libraries that extract themselves can be allowed without allowing anything else.
* The name compared is the file name without its folder, a leading `lib` and its library extension (`.so` with any
  version after it, `.dll`, `.dylib`, `.jnilib`), so dots inside the name are kept.
* `Allowlist` holds the rule, and its tests prove it with the file names these libraries really use.

## More Information

* Builds on: [Native libraries are refused unless allowed, except the JDK's and the build tool's own](0012-native-libraries-are-refused-unless-allowed-except-the-jdks-and-the-build-tools-own.md)
* Later decisions that build on this: none yet
* Background: [the design: what counts as a risky call](../design.md#what-counts-as-a-risky-call-and-where-its-caught)
