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

    /** Ending the JVM, with {@code System.exit} or {@code Runtime.halt}, while a test is running. */
    EXIT("exit");

    private final String reportName;

    Kind(final String reportName) {
        this.reportName = reportName;
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
