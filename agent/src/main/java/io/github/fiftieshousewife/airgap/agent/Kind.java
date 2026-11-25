package io.github.fiftieshousewife.airgap.agent;

/**
 * The kinds of risky call the agent catches, named as the report names them: looking up a host name; a TCP connection,
 * through a socket or a channel; a UDP connection or packet; starting another program; a JNDI lookup of a directory or
 * registry on another machine (LDAP, RMI, DNS or CORBA); loading a native library, whose code the agent can't see
 * into; writing, creating, deleting, renaming or moving a file, or making a folder, outside the project; and ending the
 * JVM with {@code System.exit} or {@code Runtime.halt} while a test is running.
 */
public enum Kind {

    HOST_LOOKUP("host-lookup"),
    TCP("tcp"),
    UDP("udp"),
    PROCESS("process"),
    JNDI("jndi"),
    NATIVE_LIBRARY("native-library"),
    FILE_WRITE("file-write"),
    EXIT("exit");

    private final String reportName;

    Kind(final String reportName) {
        this.reportName = reportName;
    }

    // Returns true for a host lookup, or a TCP or UDP call
    boolean isNetwork() {
        return this == HOST_LOOKUP || this == TCP || this == UDP;
    }

    // Returns true for the kinds the JDK makes for itself while it does another kind, so a probe lets them be
    boolean isTheJdksOwnWork() {
        return this == NATIVE_LIBRARY || this == EXIT || this == FILE_WRITE;
    }

    /**
     * The kind's name in the report.
     *
     * @return the name, for example {@code host-lookup}
     */
    public String getReportName() {
        return reportName;
    }
}
