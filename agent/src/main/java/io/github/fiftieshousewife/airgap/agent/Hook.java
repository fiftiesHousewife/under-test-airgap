package io.github.fiftieshousewife.airgap.agent;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.ExitAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiCompositeNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiNameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JndiProviderAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.LookupAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.NativeLibraryAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ProcessAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.TcpAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpAddressAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpChannelSendAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpPacketAdvice;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
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

    /** {@code InetAddress.getByName}. */
    HOST_LOOKUP_ONE("java.net.InetAddress", named("getByName").and(takesArguments(String.class)), LookupAdvice.class),

    /** {@code InetAddress.getAllByName}. */
    HOST_LOOKUP_ALL("java.net.InetAddress", named("getAllByName").and(takesArguments(String.class)),
            LookupAdvice.class),

    /** Every plain socket connect, which all of {@code Socket}'s constructors and {@code connect} methods reach. */
    TCP_SOCKET("java.net.Socket", named("connect").and(takesArguments(SocketAddress.class, int.class)),
            TcpAdvice.class),

    /** NIO socket channels, used by Netty, the JDK's HTTP client and many drivers. */
    TCP_CHANNEL("sun.nio.ch.SocketChannelImpl", named("connect").and(takesArguments(SocketAddress.class)),
            TcpAdvice.class),

    /** Asynchronous socket channels, both {@code connect} methods. */
    TCP_ASYNC("sun.nio.ch.AsynchronousSocketChannelImpl",
            named("connect").and(takesArgument(0, SocketAddress.class)), TcpAdvice.class),

    /** {@code DatagramSocket.connect(SocketAddress)}. */
    UDP_SOCKET_CONNECT("java.net.DatagramSocket", named("connect").and(takesArguments(SocketAddress.class)),
            UdpAdvice.class),

    /** {@code DatagramSocket.connect(InetAddress, int)}. */
    UDP_SOCKET_CONNECT_ADDRESS("java.net.DatagramSocket",
            named("connect").and(takesArguments(InetAddress.class, int.class)), UdpAddressAdvice.class),

    /** {@code DatagramSocket.send}, for packets that carry their own address. */
    UDP_SOCKET_SEND("java.net.DatagramSocket", named("send").and(takesArguments(DatagramPacket.class)),
            UdpPacketAdvice.class),

    /** {@code DatagramChannel.connect}, which also carries the JDK's own DNS client on newer JDKs. */
    UDP_CHANNEL_CONNECT("sun.nio.ch.DatagramChannelImpl", named("connect").and(takesArguments(SocketAddress.class)),
            UdpAdvice.class),

    /** {@code DatagramChannel.send}. */
    UDP_CHANNEL_SEND("sun.nio.ch.DatagramChannelImpl",
            named("send").and(takesArguments(ByteBuffer.class, SocketAddress.class)), UdpChannelSendAdvice.class),

    /** {@code ProcessBuilder.start}, which {@code Runtime.exec} also reaches. */
    PROCESS("java.lang.ProcessBuilder", named("start").and(takesArguments(0)), ProcessAdvice.class),

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
