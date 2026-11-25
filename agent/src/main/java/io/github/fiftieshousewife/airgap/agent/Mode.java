package io.github.fiftieshousewife.airgap.agent;

/**
 * How the agent behaves in a run.
 *
 * <ul>
 * <li>{@code REFUSE}: refuse every risky call that isn't allowed, and report it. The default.</li>
 * <li>{@code REPORT}: let every risky call go ahead, and report it. For runs whose network is already blocked.</li>
 * <li>{@code OFF}: do nothing: no hooks, no report.</li>
 * </ul>
 */
public enum Mode {
    REFUSE,
    REPORT,
    OFF
}
