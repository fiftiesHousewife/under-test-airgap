package io.github.fiftieshousewife.airgap.agent;

import java.util.Arrays;
import java.util.List;

// A JNDI URL's parts, read as text: whether its scheme's provider reaches other machines (ldap, rmi, dns and the CORBA
// schemes do; java: and a provider's own stay inside the JVM), and the host and port in its authority.
final class JndiUrls {

    static final int NO_PORT = -1;
    private static final List<String> SCHEMES_REACHING_OTHER_MACHINES = Arrays.asList("ldap", "ldaps", "rmi", "dns",
            "iiop", "iiopname", "corbaname");
    private static final int LONGEST_PORT = 5;

    private JndiUrls() {
    }

    static boolean reachesOtherMachines(final String name) {
        final int colon = name.indexOf(':');
        return colon > 0 && SCHEMES_REACHING_OTHER_MACHINES.contains(Ascii.lowerCase(name.substring(0, colon)));
    }

    static boolean isDns(final String url) {
        return Ascii.equalsIgnoringCase("dns", url.substring(0, Math.max(url.indexOf(':'), 0)));
    }

    // The authority, after any user name: "ldap://user@host:389/dc=example" gives "host:389"; none gives "".
    static String authority(final String url) {
        final String afterScheme = url.substring(url.indexOf(':') + 1);
        if (!afterScheme.startsWith("//")) {
            return "";
        }
        final String authority = afterScheme.substring(2, indexOfAny(afterScheme, 2, "/?#"));
        return authority.substring(authority.lastIndexOf('@') + 1);
    }

    static String hostOf(final String authority) {
        if (authority.startsWith("[")) {
            final int close = authority.indexOf(']');
            return close < 0 ? authority.substring(1) : authority.substring(1, close);
        }
        final int colon = authority.indexOf(':');
        return colon < 0 ? authority : authority.substring(0, colon);
    }

    static int portOf(final String authority) {
        final String digits = portText(authority);
        return digits.isEmpty() || !HostNames.onlyDigits(digits) || digits.length() > LONGEST_PORT ? NO_PORT
                : Integer.parseInt(digits);
    }

    private static String portText(final String authority) {
        final int colon = authority.lastIndexOf(':');
        return colon < 0 || authority.indexOf(']', colon) >= 0 ? "" : authority.substring(colon + 1);
    }

    private static int indexOfAny(final String text, final int from, final String characters) {
        for (int i = from; i < text.length(); i++) {
            if (characters.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return text.length();
    }
}
