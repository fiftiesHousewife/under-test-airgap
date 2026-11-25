package io.github.fiftieshousewife.airgap.agent.probes;

import io.github.fiftieshousewife.airgap.agent.Hook;
import java.io.File;
import java.util.Hashtable;
import java.util.Map;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * The probes of the program, JNDI and native-library hooks. The program started and the libraries loaded don't
 * exist, and the JNDI contexts have no provider to connect with, so each is harmless even if its hook is missing.
 */
public final class SystemProbes {

    private static final String NO_SUCH_LIBRARY = "airgap-self-check-no-such-library";
    private static final String PROBE_URL = "ldap://" + NetworkProbes.PROBE_HOST + "/o=probe";

    private SystemProbes() {
    }

    /** @param probes where to add the program, JNDI and native-library probes, by their hooks */
    public static void addTo(final Map<Hook, Probe> probes) {
        probes.put(Hook.PROCESS, () -> new ProcessBuilder("airgap-self-check-no-such-program").start());
        probes.put(Hook.JNDI_NAME, () -> new ProbeContext().lookup(PROBE_URL));
        probes.put(Hook.JNDI_COMPOSITE_NAME, () -> new ProbeContext().lookup(new CompositeName(PROBE_URL)));
        probes.put(Hook.JNDI_PROVIDER, () -> new ProbeContext().makeTheDefaultContext());
        probes.put(Hook.NATIVE_LIBRARY_BY_NAME, () -> System.loadLibrary(NO_SUCH_LIBRARY));
        probes.put(Hook.NATIVE_LIBRARY_BY_PATH, () -> System.load(noSuchLibraryFile()));
        probes.put(Hook.NATIVE_LIBRARY_RUNTIME_BY_NAME, () -> Runtime.getRuntime().loadLibrary(NO_SUCH_LIBRARY));
        probes.put(Hook.NATIVE_LIBRARY_RUNTIME_BY_PATH, () -> Runtime.getRuntime().load(noSuchLibraryFile()));
    }

    private static String noSuchLibraryFile() {
        return new File(System.getProperty("java.io.tmpdir"), NO_SUCH_LIBRARY).getAbsolutePath();
    }

    // A JNDI context with no settings read and no provider: a lookup of a URL fails without connecting, and making
    // its default context fails for want of a provider, if the hooks don't stop them first.
    @SuppressWarnings("PMD.ReplaceHashtableWithMap") // JNDI's own type
    private static final class ProbeContext extends InitialContext {

        ProbeContext() throws NamingException {
            super(true);
        }

        void makeTheDefaultContext() throws NamingException {
            final Hashtable<Object, Object> environment = new Hashtable<>();
            environment.put(Context.PROVIDER_URL, "ldap://" + NetworkProbes.PROBE_HOST + ":389/o=probe");
            myProps = environment;
            getDefaultInitCtx();
        }
    }
}
