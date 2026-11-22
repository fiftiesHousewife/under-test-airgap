package io.github.fiftieshousewife.airgap.agent;

/** Where the agent records each risky call. */
public interface Report {

    /**
     * Records one call.
     *
     * @param call the call
     */
    void record(Call call);
}
