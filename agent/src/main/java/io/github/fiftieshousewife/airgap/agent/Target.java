package io.github.fiftieshousewife.airgap.agent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * What a risky call tried to reach: a host and port, a JNDI URL, a program, a native library or an exit. Worked out
 * without any lookup of its own, so it never makes the risky call it describes.
 */
@Builder(access = AccessLevel.PRIVATE)
public final class Target {

    private static final int NO_PORT = -1;
    /** JNDI URL schemes whose providers reach other machines. Others ({@code java:}, a provider's own) stay inside. */
    private static final List<String> NETWORK_JNDI_SCHEMES = Arrays.asList("ldap", "ldaps", "rmi", "dns", "iiop",
            "iiopname", "corbaname");
    private static final List<String> LOCAL_NAMES = Arrays.asList("localhost", "localhost.localdomain",
            "ip6-localhost", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    /** How far away a target is. */
    private enum Reach {
        /** The machine itself. */
        LOCAL,
        /** Another machine, at a known address. */
        REMOTE,
        /** Another machine, by a name nobody has looked up yet. */
        UNRESOLVED
    }

    /** What the allowlist matches: the host, the program or the library. */
    private final String name;
    @Builder.Default
    private final int port = NO_PORT;
    private final Reach reach;
    /** What the report shows, when it isn't the name and port: a JNDI URL. */
    private final String shown;

    private static Target of(final String name, final int port, final Reach reach) {
        return builder().name(name).port(port).reach(reach).build();
    }

    private static Reach localOrRemote(final boolean local) {
        return local ? Reach.LOCAL : Reach.REMOTE;
    }

    /**
     * A host name being looked up. Looking up the machine itself, or an address written as numbers (which needs no
     * lookup), counts as local.
     *
     * @param host the name, as given to the lookup
     * @return the target
     */
    public static Target ofLookup(final String host) {
        return of(host, NO_PORT, localOrRemote(isLocalName(host) || isAddressLiteral(host)));
    }

    /**
     * Where a connection or packet is going.
     *
     * @param address the address given to the connect or send
     * @return the target; the machine's own addresses count as local, and so does anything other than an internet
     *     address (a Unix domain socket, say)
     */
    public static Target ofAddress(final SocketAddress address) {
        if (!(address instanceof InetSocketAddress)) {
            return of(String.valueOf(address), NO_PORT, Reach.LOCAL);
        }
        final InetSocketAddress internet = (InetSocketAddress) address;
        final String host = internet.getHostString();
        if (internet.isUnresolved()) {
            return of(host, internet.getPort(), isLocalName(host) ? Reach.LOCAL : Reach.UNRESOLVED);
        }
        final InetAddress resolved = internet.getAddress();
        return of(host, internet.getPort(), localOrRemote(isLocalAddress(resolved) || isThisMachines(resolved)));
    }

    /**
     * Where a connection or packet is going, as an address and a port.
     *
     * @param address the address, or {@code null} for none
     * @param port the port
     * @return the target
     */
    public static Target ofAddress(final InetAddress address, final int port) {
        if (address == null) {
            return of("(none)", port, Reach.LOCAL);
        }
        return ofAddress(new InetSocketAddress(address, port));
    }

    /**
     * A program being started.
     *
     * @param command the command and its arguments
     * @return the target, named by the program
     */
    public static Target ofCommand(final List<String> command) {
        final String program = command.isEmpty() ? "" : command.get(0);
        return of(program, NO_PORT, Reach.REMOTE);
    }

    /**
     * Whether a JNDI name is a URL whose provider reaches another machine.
     *
     * @param name the name given to the lookup
     * @return true for {@code ldap:}, {@code ldaps:}, {@code rmi:}, {@code dns:} and the CORBA schemes
     */
    public static boolean isNetworkJndiName(final String name) {
        final int colon = name.indexOf(':');
        return colon > 0 && NETWORK_JNDI_SCHEMES.contains(Ascii.lowerCase(name.substring(0, colon)));
    }

    /**
     * A JNDI URL being looked up, judged by its host. With no host, LDAP, RMI and CORBA providers use the machine
     * itself, and DNS uses the system's DNS servers.
     *
     * @param url the URL, such as {@code ldap://ldap.example.com:389/dc=example,dc=com}
     * @return the target, shown as the URL
     */
    public static Target ofJndi(final String url) {
        final int colon = url.indexOf(':');
        final boolean dns = Ascii.equalsIgnoringCase("dns", url.substring(0, Math.max(colon, 0)));
        final String afterScheme = url.substring(colon + 1);
        String authority = "";
        if (afterScheme.startsWith("//")) {
            final int end = indexOfAny(afterScheme, 2, "/?#");
            authority = afterScheme.substring(2, end);
        }
        authority = authority.substring(authority.lastIndexOf('@') + 1);
        final String host = hostOf(authority);
        final Reach reach = host.isEmpty() ? localOrRemote(!dns) : localOrRemote(isLocalName(host));
        return builder().name(host).port(portOf(authority)).reach(reach).shown(url).build();
    }

    private static int indexOfAny(final String text, final int from, final String characters) {
        for (int i = from; i < text.length(); i++) {
            if (characters.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return text.length();
    }

    private static String hostOf(final String authority) {
        if (authority.startsWith("[")) {
            final int close = authority.indexOf(']');
            return close < 0 ? authority.substring(1) : authority.substring(1, close);
        }
        final int colon = authority.indexOf(':');
        return colon < 0 ? authority : authority.substring(0, colon);
    }

    private static int portOf(final String authority) {
        final int colon = authority.lastIndexOf(':');
        final String digits = colon < 0 || authority.indexOf(']', colon) >= 0 ? "" : authority.substring(colon + 1);
        return digits.isEmpty() || !onlyDigits(digits) || digits.length() > 5 ? NO_PORT : Integer.parseInt(digits);
    }

    /**
     * An address whose name is being looked up.
     *
     * @param address the address
     * @return the target, named by the address as written; the machine itself counts as local
     */
    public static Target ofReverseLookup(final InetAddress address) {
        return of(address.getHostAddress(), NO_PORT, localOrRemote(isLocalAddress(address)));
    }

    /**
     * A native library being loaded.
     *
     * @param library its name as given to {@code loadLibrary}, or its path as given to {@code load}
     * @return the target
     */
    public static Target ofLibrary(final String library) {
        return of(library, NO_PORT, Reach.REMOTE);
    }

    /**
     * The JVM being ended.
     *
     * @param method {@code exit} or {@code halt}
     * @param status the status it would end with
     * @return the target, shown as {@code exit(1)}
     */
    public static Target ofExit(final String method, final int status) {
        return of(method + "(" + status + ")", NO_PORT, Reach.REMOTE);
    }

    /**
     * Whether a host name names the machine itself.
     *
     * @param host the name
     * @return true for {@code localhost} and the loopback addresses
     */
    public static boolean isLocalName(final String host) {
        for (final String name : LOCAL_NAMES) {
            if (Ascii.equalsIgnoringCase(name, host)) {
                return true;
            }
        }
        return isIpv4(host) && host.startsWith("127.") || Ascii.endsWithIgnoringCase(host, ".localhost");
    }

    /** Whether a host is an address written as numbers, which needs no lookup. Checked by hand, not by pattern. */
    private static boolean isAddressLiteral(final String host) {
        return isIpv4(host) || host.indexOf(':') >= 0 && onlyIpv6Characters(host);
    }

    private static boolean isIpv4(final String host) {
        final String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (final String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !onlyDigits(part) || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean onlyDigits(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean onlyIpv6Characters(final String host) {
        for (int i = 0; i < host.length(); i++) {
            if ("0123456789abcdefABCDEF:.%[]".indexOf(host.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLocalAddress(final InetAddress address) {
        return address.isLoopbackAddress() || address.isAnyLocalAddress();
    }

    /**
     * Whether an address is one of the machine's own network interfaces' addresses, such as the one RMI and JMX hand
     * out for objects in the same JVM. Asked of the operating system, which sends nothing on the network. Not used for
     * reverse lookups: the name of a network address comes from DNS, off the machine.
     */
    static boolean isThisMachines(final InetAddress address) {
        try {
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (SocketException unknown) {
            return false;
        }
    }

    /** @return the host name or address, the program, the library or the exit */
    public String getName() {
        return name;
    }

    /** @return the port, or -1 if there is none */
    public int getPort() {
        return port;
    }

    /** @return whether the target is the machine itself, and so always allowed and never reported */
    public boolean isLocal() {
        return reach == Reach.LOCAL;
    }

    /** @return whether the address was never looked up, so an unplugged machine would fail to look it up */
    public boolean isUnresolved() {
        return reach == Reach.UNRESOLVED;
    }

    /** @return the target as the report shows it: {@code host:port}, a URL, or the name alone */
    public String text() {
        if (shown != null) {
            return shown;
        }
        return port == NO_PORT ? name : name + ":" + port;
    }
}
