package example.airgap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import javax.naming.CommunicationException;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// JNDI lookups of directories on other machines, made for real with the agent attached, are refused as an unplugged
// machine would refuse them; names inside the JVM go ahead. Every target is a name that can't resolve or an address
// reserved for documentation. The timeout allows for a cold start: the first JNDI call alone takes around half a
// second.
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class RefusedJndiCallsTest {

    private static final String UNRESOLVABLE = "db.prod.example.invalid";
    private static final String DOCUMENTATION_ADDRESS = "192.0.2.1";

    @Test
    void refusesJndiLookupsOfOtherMachines() {
        assertAll(
                () -> assertThatThrownBy(() -> new InitialContext().lookup("ldap://" + UNRESOLVABLE + "/o=orders"))
                        .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> new InitialContext().lookup(
                        new CompositeName("rmi://" + DOCUMENTATION_ADDRESS + ":1099/orders")))
                        .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> new InitialDirContext().getAttributes(
                        "dns://" + DOCUMENTATION_ADDRESS + "/example.com"))
                        .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesADirectoryNamedInTheJndiSettings() {
        final Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://" + UNRESOLVABLE + ":389/dc=example,dc=com");

        assertThatThrownBy(() -> new InitialDirContext(environment))
                .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED);
    }

    @Test
    void leavesJndiNamesInsideTheJvmAlone() {
        assertThatThrownBy(() -> new InitialContext().lookup("java:comp/env/jdbc/orders"))
                .isInstanceOf(NamingException.class).hasMessageNotContaining(Refusals.REFUSED);
    }
}
