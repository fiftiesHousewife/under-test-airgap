package io.github.fiftieshousewife.airgap.agent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * What a risky call tried to reach: a host and port, a JNDI URL, a program, a native library or an exit. Worked out
 * without any lookup of its own, so it never makes the risky call it describes. Its name is what the allowlist
 * matches: the host, the program or the library; a JNDI URL is shown in the report as the URL. Each target is the
 * machine itself (local), another machine at a known address (remote), or another machine by a name nobody has looked
 * up yet (unresolved).
 */
@Builder(access = AccessLevel.PRIVATE)
public final class Target {

    private static final int NO_PORT = JndiUrls.NO_PORT;

    private enum Reach { LOCAL, REMOTE, UNRESOLVED }

    private final String name;
    @Builder.Default
    private final int port = NO_PORT;
    private final Reach reach;
    private final String shownInReport;
    private final String proxy;

    private static Target of(final String name, final int port, final Reach reach) {
        return builder().name(name).port(port).reach(reach).build();
    }

    private static Reach localOrRemote(final boolean local) {
        return local ? Reach.LOCAL : Reach.REMOTE;
    }

    /** @return a host name being looked up; the machine itself, or an address written as numbers, is local */
    public static Target ofLookup(final String host) {
        return of(host, NO_PORT, localOrRemote(isLocalName(host) || HostNames.isAddressLiteral(host)));
    }

    /**
     * @return where a connection or packet is going; the machine's own addresses are local, and so is anything other
     *     than an internet address (a Unix domain socket, say)
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
        return of(host, internet.getPort(),
                localOrRemote(HostNames.isLocalAddress(resolved) || HostNames.isThisMachines(resolved)));
    }

    /** @return where a connection or packet is going, as an address (or {@code null} for none) and a port */
    public static Target ofAddress(final InetAddress address, final int port) {
        if (address == null) {
            return of("(none)", port, Reach.LOCAL);
        }
        return ofAddress(new InetSocketAddress(address, port));
    }

    /** @return a host and port reached through an HTTP proxy, given as {@code host:port}, which the report names */
    public static Target ofProxiedRequest(final String host, final int port, final String proxy) {
        return builder().name(host).port(port).reach(isLocalName(host) ? Reach.LOCAL : Reach.UNRESOLVED).proxy(proxy)
                .build();
    }

    /** @return a program being started, named by the first of its command and arguments */
    public static Target ofCommand(final List<String> command) {
        final String program = command.isEmpty() ? "" : command.get(0);
        return of(program, NO_PORT, Reach.REMOTE);
    }

    /**
     * @return a JNDI URL being looked up, such as {@code ldap://ldap.example.com:389/dc=x}, judged by its host
     *     and shown as the URL; with no host, LDAP, RMI and CORBA use the machine itself, DNS the system's servers
     */
    public static Target ofJndi(final String url) {
        final String authority = JndiUrls.authority(url);
        final String host = JndiUrls.hostOf(authority);
        final Reach reach = host.isEmpty() ? localOrRemote(!JndiUrls.isDns(url)) : localOrRemote(isLocalName(host));
        return builder().name(host).port(JndiUrls.portOf(authority)).reach(reach).shownInReport(url).build();
    }

    /** @return an address whose name is being looked up, named by the address as written */
    public static Target ofReverseLookup(final InetAddress address) {
        return of(address.getHostAddress(), NO_PORT, localOrRemote(HostNames.isLocalAddress(address)));
    }

    /** @return a file or folder being written, deleted, moved or made outside the project, by its absolute path */
    public static Target ofFile(final String path) {
        return of(path, NO_PORT, Reach.REMOTE);
    }

    /** @return a native library being loaded: its name, as given to {@code loadLibrary}, or path, to {@code load} */
    public static Target ofLibrary(final String library) {
        return of(library, NO_PORT, Reach.REMOTE);
    }

    /** @return the JVM being ended by {@code exit} or {@code halt}, with a status: shown as {@code exit(1)} */
    public static Target ofExit(final String method, final int status) {
        return of(method + "(" + status + ")", NO_PORT, Reach.REMOTE);
    }

    /** @return true for a host name that names the machine itself: {@code localhost} and the loopback addresses */
    public static boolean isLocalName(final String host) {
        return HostNames.isLocalName(host);
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

    /** @return the HTTP proxy the call goes through, as {@code host:port}, or {@code null} for none */
    public String getProxy() {
        return proxy;
    }

    /** @return the target as the report shows it: {@code host:port}, a URL, or the name alone */
    public String text() {
        if (shownInReport != null) {
            return shownInReport;
        }
        return port == NO_PORT ? name : name + ":" + port;
    }
}
