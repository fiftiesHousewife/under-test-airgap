package io.github.fiftieshousewife.airgap.agent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * What a risky call tried to reach: a host and port, or a program. Worked out without any lookup of its own, so it
 * never makes the risky call it describes.
 */
public final class Target {

    private static final int NO_PORT = -1;
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

    private final String name;
    private final int port;
    private final Reach reach;

    private Target(final String name, final int port, final Reach reach) {
        this.name = name;
        this.port = port;
        this.reach = reach;
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
        return new Target(host, NO_PORT, localOrRemote(isLocalName(host) || isAddressLiteral(host)));
    }

    /**
     * Where a connection or packet is going.
     *
     * @param address the address given to the connect or send
     * @return the target; anything other than an internet address (a Unix domain socket, say) counts as local
     */
    public static Target ofAddress(final SocketAddress address) {
        if (!(address instanceof InetSocketAddress)) {
            return new Target(String.valueOf(address), NO_PORT, Reach.LOCAL);
        }
        final InetSocketAddress internet = (InetSocketAddress) address;
        final String host = internet.getHostString();
        if (internet.isUnresolved()) {
            return new Target(host, internet.getPort(), isLocalName(host) ? Reach.LOCAL : Reach.UNRESOLVED);
        }
        return new Target(host, internet.getPort(), localOrRemote(isLocalAddress(internet.getAddress())));
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
            return new Target("(none)", port, Reach.LOCAL);
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
        return new Target(program, NO_PORT, Reach.REMOTE);
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
        return host.startsWith("127.") || Ascii.endsWithIgnoringCase(host, ".localhost");
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
            if (part.isEmpty() || part.length() > 3 || !onlyDigits(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean onlyDigits(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
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

    /** @return the host name or address, or the program */
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

    /** @return the target as the report shows it: {@code host:port}, or the name alone */
    public String text() {
        return port == NO_PORT ? name : name + ":" + port;
    }
}
