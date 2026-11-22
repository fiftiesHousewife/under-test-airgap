package io.github.fiftieshousewife.airgap.agent;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.LookupAdvice;
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
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Every hook: the JDK class and methods it rewrites, and the advice it adds. Each is the lowest point in the JDK that
 * every route to that kind of call goes through, on JDK 8 and every version since; {@link SelfCheck} proves each one
 * at start.
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
    PROCESS("java.lang.ProcessBuilder", named("start").and(takesArguments(0)), ProcessAdvice.class);

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
