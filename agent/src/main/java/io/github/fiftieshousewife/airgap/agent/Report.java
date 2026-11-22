package io.github.fiftieshousewife.airgap.agent;

import java.io.IOException;

/** Where the agent records each risky call. */
public interface Report {

    /**
     * Records one call.
     *
     * @param call the call
     * @throws IOException if it couldn't be recorded
     */
    void record(Call call) throws IOException;
}
