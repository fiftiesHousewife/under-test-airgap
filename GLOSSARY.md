# Glossary and word list

Propose changes by editing this file in a pull request. Check new documents against it.

## Rules for words

1. **Use common English in its everyday sense.** Don't give an ordinary word a special meaning.
2. **Industry-standard technique and tool names are allowed** (Java agent, mutation testing, JNDI), but each is
   explained in plain words the first time it appears in a document.
3. **No letter or number codes.** Write the thing's name, never a short code for it.
4. **Spell out abbreviations** the first time.
5. **One word per idea.** If two things share a word, rename one.
6. **Names say what something is or does.** A file, decision or task name should make sense to a newcomer.
7. **Refer to things by name, with a link.** Never by a code or number.
8. **Nothing about any one organisation.** Name a product only as an option or an example.

## Words we use

| Word or phrase | Meaning | Don't use |
|---|---|---|
| **airgap agent** | This repo's Java agent: a jar named on the JVM's command line (`-javaagent:`) that refuses risky calls and reports every call. An air gap usually means a machine with no network at all; here it means the JVM behaves as if it had none, for ordinary code. | proxy (keep that word for a web proxy), sandbox, firewall |
| **Java agent** | A jar the JVM loads before the program, allowed to rewrite classes as they load. Standard Java; needs no administrator rights. | |
| **risky call** | A call that could reach outside the machine or change it: a host lookup, a network connection, a JNDI or LDAP lookup, starting a program, loading native code, ending the JVM, writing a file outside the project. | dangerous call, side effect |
| **hook** | The place in the JDK where the agent catches one kind of risky call, and the code it adds there. Hooks are specific to a range of JDK versions. | interceptor, probe |
| **refuse** | Stop a risky call from happening, by throwing the exception an unplugged machine would throw. | block (in prose), deny |
| **allowlist** | The calls that may go ahead: the machine itself, the package repository for the build, and the stand-ins a project's tests use. | whitelist |
| **stand-in** | Something a test uses in place of a real system: a local WireMock server, an in-memory fake, a local database. | mock (for a whole system), double |
| **connection list** | The list of everything a project's code connects to; the agent's report is its first draft. | dependency map |
| **report** | `build/airgap/calls.jsonl`: one line per call, refused or allowed, with who made it. | log (for the report itself) |
| **mode** | How the agent behaves in a run: `refuse` (the default), `report` (record only) or `off`. | |
| **JNDI** | Java Naming and Directory Interface: the JDK's way of looking things up by name, including on LDAP and RMI servers. | |
