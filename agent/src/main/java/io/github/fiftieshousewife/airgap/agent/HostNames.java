package io.github.fiftieshousewife.airgap.agent;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;

// Whether a host name or address is the machine itself, or an address written as numbers, which needs no lookup.
// Checked by hand, not by pattern, and without any lookup, so checking never makes the call it describes.
final class HostNames {

    private static final List<String> LOCAL_NAMES = Arrays.asList("localhost", "localhost.localdomain",
            "ip6-localhost", "::1", "[::1]", "0:0:0:0:0:0:0:1");
    private static final int HIGHEST_OCTET = 255;
    private static final int LONGEST_OCTET = 3;

    private HostNames() {
    }

    // True for localhost, the loopback addresses, and names under .localhost.
    static boolean isLocalName(final String host) {
        for (final String name : LOCAL_NAMES) {
            if (Ascii.equalsIgnoringCase(name, host)) {
                return true;
            }
        }
        return isIpv4(host) && host.startsWith("127.") || Ascii.endsWithIgnoringCase(host, ".localhost");
    }

    static boolean isAddressLiteral(final String host) {
        return isIpv4(host) || host.indexOf(':') >= 0 && onlyIpv6Characters(host);
    }

    static boolean isLocalAddress(final InetAddress address) {
        return address.isLoopbackAddress() || address.isAnyLocalAddress();
    }

    // Whether an address is one of the machine's own network interfaces' addresses, such as the one RMI and JMX hand
    // out for objects in the same JVM. Asked of the operating system, which sends nothing on the network. Not used for
    // reverse lookups: the name of a network address comes from DNS, off the machine.
    static boolean isThisMachines(final InetAddress address) {
        try {
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (SocketException unknown) {
            return false;
        }
    }

    static boolean isIpv4(final String host) {
        final String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (final String part : parts) {
            if (!isOctet(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOctet(final String part) {
        return !part.isEmpty() && part.length() <= LONGEST_OCTET && onlyDigits(part)
                && Integer.parseInt(part) <= HIGHEST_OCTET;
    }

    static boolean onlyDigits(final String text) {
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
}
