package io.github.fiftieshousewife.airgap.agent;

/** How the agent behaves in a run. */
public enum Mode {

    /** Refuse every risky call that isn't allowed, and report it. The default. */
    REFUSE,

    /** Let every risky call go ahead, and report it. For runs whose network is already blocked. */
    REPORT,

    /** Do nothing: no hooks, no report. */
    OFF
}
