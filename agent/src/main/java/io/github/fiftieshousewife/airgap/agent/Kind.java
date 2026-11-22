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
    PROCESS("process");

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
