package io.github.fiftieshousewife.airgap.agent.probes;

import java.io.IOException;
import javax.naming.NamingException;

/** A hooked call, made by the check at start only to see whether its hook is reached. */
public interface Probe {

    /**
     * Makes the call.
     *
     * @throws IOException as the call may
     * @throws NamingException as a JNDI call may
     * @throws ReflectiveOperationException as a call made by reflection may
     */
    void call() throws IOException, NamingException, ReflectiveOperationException;
}
