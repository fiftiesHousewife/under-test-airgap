package io.github.fiftieshousewife.airgap.agent;

/** The kinds of risky call the agent catches, named as the report names them. */
public enum Kind {

    /** Looking up a host name. */
    HOST_LOOKUP("host-lookup"),

    /** A TCP connection, through a socket or a channel. */
    TCP("tcp"),

    /** A UDP connection or packet. */
    UDP("udp"),

    /** Starting another program. */
    PROCESS("process"),

    /** A JNDI lookup of a directory or registry on another machine: LDAP, RMI, DNS or CORBA. */
    JNDI("jndi"),

    /** Loading a native library, whose code the agent can't see into. */
    NATIVE_LIBRARY("native-library"),

    /** Writing, creating, deleting, renaming or moving a file, or making a folder, outside the project. */
    FILE_WRITE("file-write"),

    /** Ending the JVM, with {@code System.exit} or {@code Runtime.halt}, while a test is running. */
    EXIT("exit");

    private final String reportName;

    Kind(final String reportName) {
        this.reportName = reportName;
    }

    /** @return true for the kinds the JDK makes for itself while it does another kind, so a probe lets them be */
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
