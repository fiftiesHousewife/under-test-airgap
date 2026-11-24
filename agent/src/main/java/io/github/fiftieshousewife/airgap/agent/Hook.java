package io.github.fiftieshousewife.airgap.agent;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.AsyncConnectAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.AsyncConnectHandlerAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ExitAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ForeignLibraryAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.HandOverAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.HandOverExecutorAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiCompositeNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiProviderAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.LookupAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.NativeLibraryAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.OwnNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ProcessAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ReachabilityAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ReverseLookupAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.SctpSendAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.TcpAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpAddressAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpChannelSendAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpPacketAdvice;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;
import javax.naming.Name;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Every hook: the JDK class and methods it rewrites, and the advice it adds. Each is the lowest point in the JDK that
 * every route to that kind of call goes through, on JDK 8 and every version since; {@link SelfCheck} proves each one
 * at start. The JNDI hooks only see names: the connections JNDI providers then make are also caught by the network
 * hooks, which is how a context made without a URL is caught.
 */
public enum Hook {

    /**
     * The JDK's own forward lookup, above its cache, which {@code getByName}, {@code getAllByName} and the JDK's own
     * name checks (in {@code SocketPermission}, security policies and RMI) all reach: on each JDK, its private
     * {@code getAllByName0} with the most parameters.
     */
    HOST_LOOKUP_ALL("java.net.InetAddress", HookMethods.innermostLookup(), LookupAdvice.class),

    /** {@code InetAddress.getLocalHost}: the machine asking its own name, which is local, so lookups in it go ahead. */
    HOST_LOOKUP_OWN_NAME("java.net.InetAddress", named("getLocalHost").and(takesArguments(0)), OwnNameAdvice.class),

    /** Reverse lookups: {@code getHostName} and {@code getCanonicalHostName}, for an address with no name. */
    HOST_LOOKUP_REVERSE("java.net.InetAddress",
            named("getHostFromNameService").and(takesArgument(0, InetAddress.class)), ReverseLookupAdvice.class),

    /** Every plain socket connect, which all of {@code Socket}'s constructors and {@code connect} methods reach. */
    TCP_SOCKET("java.net.Socket", named("connect").and(takesArguments(SocketAddress.class, int.class)),
            TcpAdvice.class),

    /**
     * The socket a {@code SocketChannel} hands out: from JDK 13 its {@code connect} goes straight to the channel's
     * blocking connect, past both the channel's and {@code Socket}'s.
     */
    TCP_SOCKET_ADAPTOR("sun.nio.ch.SocketAdaptor", named("connect").and(takesArguments(SocketAddress.class, int.class)),
            TcpAdvice.class),

    /** {@code InetAddress.isReachable}, which sends ICMP or TCP echo packets to the address. */
    TCP_REACHABILITY("java.net.InetAddress",
            named("isReachable").and(takesArguments(NetworkInterface.class, int.class, int.class)),
            ReachabilityAdvice.class),

    /** SCTP channels' connect, where the JDK supports SCTP (Linux). */
    TCP_SCTP_CONNECT("sun.nio.ch.sctp.SctpChannelImpl", named("connect").and(takesArguments(SocketAddress.class)),
            TcpAdvice.class),

    /** SCTP multi-channels' send, which makes an association with the address the message names. */
    TCP_SCTP_SEND("sun.nio.ch.sctp.SctpMultiChannelImpl",
            named("send").and(takesArguments(2)).and(takesArgument(0, ByteBuffer.class)), SctpSendAdvice.class),

    /** NIO socket channels, used by Netty, the JDK's HTTP client and many drivers. */
    TCP_CHANNEL("sun.nio.ch.SocketChannelImpl", named("connect").and(takesArguments(SocketAddress.class)),
            TcpAdvice.class),

    /** Asynchronous socket channels' {@code connect} that returns a future. */
    TCP_ASYNC("sun.nio.ch.AsynchronousSocketChannelImpl",
            named("connect").and(takesArguments(SocketAddress.class)), AsyncConnectAdvice.class),

    /**
     * The JDK HTTP client's executor, from JDK 11: its tasks carry the test and caller of the thread that handed them
     * over, so calls made on the client's own threads are allowed and named as the request's sender. Refuses nothing
     * itself; the client's connects are refused by the channel hooks.
     */
    TCP_HTTP_CLIENT_TASKS("jdk.internal.net.http.HttpClientImpl$DelegatingExecutor",
            named("execute").and(takesArguments(Runnable.class)), HandOverAdvice.class),

    /**
     * The JDK HTTP client's private {@code sendAsync}, which every request reaches, from JDK 11: the executor it is
     * given runs the request's tasks with the sender's test and caller.
     */
    TCP_HTTP_CLIENT_SEND("jdk.internal.net.http.HttpClientImpl",
            named("sendAsync").and(isPrivate()).and(takesArguments(4)), HandOverExecutorAdvice.class),

    /** Asynchronous socket channels' {@code connect} that calls a handler. */
    TCP_ASYNC_HANDLER("sun.nio.ch.AsynchronousSocketChannelImpl",
            named("connect").and(takesArguments(3)).and(takesArgument(0, SocketAddress.class)),
            AsyncConnectHandlerAdvice.class),

    /** {@code DatagramSocket.connect(SocketAddress)}. */
    UDP_SOCKET_CONNECT("java.net.DatagramSocket", named("connect").and(takesArguments(SocketAddress.class)),
            UdpAdvice.class),

    /** {@code DatagramSocket.connect(InetAddress, int)}. */
    UDP_SOCKET_CONNECT_ADDRESS("java.net.DatagramSocket",
            named("connect").and(takesArguments(InetAddress.class, int.class)), UdpAddressAdvice.class),

    /** {@code DatagramSocket.send}, for packets that carry their own address. */
    UDP_SOCKET_SEND("java.net.DatagramSocket", named("send").and(takesArguments(DatagramPacket.class)),
            UdpPacketAdvice.class),

    /** {@code MulticastSocket.send} with a time-to-live, which doesn't go through {@code DatagramSocket.send}. */
    UDP_MULTICAST_SEND("java.net.MulticastSocket",
            named("send").and(takesArguments(DatagramPacket.class, byte.class)), UdpPacketAdvice.class),

    /** {@code DatagramChannel.connect}, which also carries the JDK's own DNS client on newer JDKs. */
    UDP_CHANNEL_CONNECT("sun.nio.ch.DatagramChannelImpl", named("connect").and(takesArguments(SocketAddress.class)),
            UdpAdvice.class),

    /** {@code DatagramChannel.send}. */
    UDP_CHANNEL_SEND("sun.nio.ch.DatagramChannelImpl",
            named("send").and(takesArguments(ByteBuffer.class, SocketAddress.class)), UdpChannelSendAdvice.class),

    /**
     * {@code ProcessBuilder.start}, which {@code Runtime.exec} also reaches. From JDK 9 the private
     * {@code start(Redirect[])} is hooked, which {@code start()} and {@code startPipeline} both reach.
     */
    PROCESS("java.lang.ProcessBuilder", HookMethods.processStart(), ProcessAdvice.class),

    /** JNDI names written as strings: every lookup, bind and list on an {@code InitialContext} reaches this. */
    JNDI_NAME("javax.naming.InitialContext", named("getURLOrDefaultInitCtx").and(takesArguments(String.class)),
            JndiNameAdvice.class),

    /** JNDI names given as {@link Name} objects. */
    JNDI_COMPOSITE_NAME("javax.naming.InitialContext",
            named("getURLOrDefaultInitCtx").and(takesArguments(Name.class)), JndiCompositeNameAdvice.class),

    /** The directory a context's settings name, as its provider URL. */
    JNDI_PROVIDER("javax.naming.InitialContext", named("getDefaultInitCtx").and(takesArguments(0)),
            JndiProviderAdvice.class),

    /** {@code System.loadLibrary}. */
    NATIVE_LIBRARY_BY_NAME("java.lang.System", named("loadLibrary").and(takesArguments(String.class)),
            NativeLibraryAdvice.class),

    /** {@code System.load}. */
    NATIVE_LIBRARY_BY_PATH("java.lang.System", named("load").and(takesArguments(String.class)),
            NativeLibraryAdvice.class),

    /** {@code Runtime.loadLibrary}. */
    NATIVE_LIBRARY_RUNTIME_BY_NAME("java.lang.Runtime", named("loadLibrary").and(takesArguments(String.class)),
            NativeLibraryAdvice.class),

    /** {@code Runtime.load}. */
    NATIVE_LIBRARY_RUNTIME_BY_PATH("java.lang.Runtime", named("load").and(takesArguments(String.class)),
            NativeLibraryAdvice.class),

    /** JDK 22's foreign function API loading a library by name: {@code SymbolLookup.libraryLookup(String, Arena)}. */
    NATIVE_LIBRARY_FOREIGN_BY_NAME("java.lang.foreign.SymbolLookup",
            named("libraryLookup").and(takesArguments(2)).and(takesArgument(0, String.class)),
            ForeignLibraryAdvice.class),

    /** {@code SymbolLookup.libraryLookup(Path, Arena)}. */
    NATIVE_LIBRARY_FOREIGN_BY_PATH("java.lang.foreign.SymbolLookup",
            named("libraryLookup").and(takesArguments(2)).and(takesArgument(0, Path.class)),
            ForeignLibraryAdvice.class),

    /** File writes: see {@link FileHooks} for each. */
    FILE_WRITE_STREAM(FileHooks.STREAM),
    FILE_WRITE_RANDOM_ACCESS(FileHooks.RANDOM_ACCESS),
    FILE_WRITE_CHANGE(FileHooks.CHANGE),
    FILE_WRITE_RENAME(FileHooks.RENAME),
    FILE_WRITE_CREATE(FileHooks.CREATE),
    FILE_WRITE_TEMP_FILE(FileHooks.TEMP_FILE),
    FILE_WRITE_CHANNEL(FileHooks.CHANNEL),
    FILE_WRITE_PATH(FileHooks.PATH),
    FILE_WRITE_COPY(FileHooks.COPY),
    FILE_WRITE_MOVE(FileHooks.MOVE),
    FILE_WRITE_SECURE_FOLDER(FileHooks.SECURE_FOLDER),
    FILE_WRITE_SECURE_DELETE(FileHooks.SECURE_DELETE),
    FILE_WRITE_SECURE_MOVE(FileHooks.SECURE_MOVE),
    FILE_WRITE_SECURE_CHANNEL(FileHooks.SECURE_CHANNEL),

    /** {@code System.exit} and {@code Runtime.exit}. */
    EXIT("java.lang.Runtime", named("exit").and(takesArguments(int.class)), ExitAdvice.class),

    /** {@code Runtime.halt}. */
    HALT("java.lang.Runtime", named("halt").and(takesArguments(int.class)), ExitAdvice.class);

    private final String typeName;
    private final ElementMatcher<? super MethodDescription> methods;
    private final Class<?> advice;

    Hook(final String typeName, final ElementMatcher<? super MethodDescription> methods, final Class<?> advice) {
        this.typeName = typeName;
        this.methods = methods;
        this.advice = advice;
    }

    Hook(final FileHooks.Spec spec) {
        this(spec.getTypeName(), spec.getMethods(), spec.getAdvice());
    }

    /**
     * Whether the hook has anything to rewrite on this JDK. The socket adaptor goes round the channel's connect only
     * from JDK 13; SCTP, the foreign function API, the HTTP client and the secure directory stream are in some JDKs
     * only; every other hook is in every one.
     *
     * @return true if the hook applies to the running JDK
     */
    public boolean appliesHere() {
        if (this == TCP_SOCKET_ADAPTOR) {
            return HookMethods.declares("sun.nio.ch.SocketChannelImpl", "blockingConnect");
        }
        return !HookMethods.SOMETIMES_THERE.contains(name()) || HookMethods.declares(typeName, "");
    }

    /**
     * Whether the hook refuses calls. The HTTP client's hooks only hand the sender's test and caller to the client's
     * threads, and the secure directory stream's constructor only notes its folder, so a route through them isn't
     * guarded by them.
     *
     * @return false for the hooks that only hand over
     */
    public boolean refuses() {
        return this != TCP_HTTP_CLIENT_TASKS && this != TCP_HTTP_CLIENT_SEND && this != FILE_WRITE_SECURE_FOLDER;
    }

    /** @return the hook's name in settings and messages, for example {@code tcp-socket} */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** @return the kind of call the hook catches */
    public Kind kind() {
        for (final Kind kind : Kind.values()) {
            if (name().startsWith(kind.name())) {
                return kind;
            }
        }
        return Kind.EXIT;
    }

    /** @return the JDK class the hook rewrites */
    public String getTypeName() {
        return typeName;
    }

    /** @return the methods the hook rewrites */
    public ElementMatcher<? super MethodDescription> getMethods() {
        return methods;
    }

    /** @return the advice added to those methods */
    public Class<?> getAdvice() {
        return advice;
    }
}
