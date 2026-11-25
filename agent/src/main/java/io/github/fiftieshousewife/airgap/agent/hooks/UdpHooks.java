package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.UdpAddressAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpChannelSendAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UdpPacketAdvice;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * The UDP hooks.
 *
 * <ul>
 * <li>{@code SOCKET_CONNECT}, {@code SOCKET_CONNECT_ADDRESS}: {@code DatagramSocket.connect(SocketAddress)} and
 * {@code DatagramSocket.connect(InetAddress, int)}.</li>
 * <li>{@code SOCKET_SEND}: {@code DatagramSocket.send}, for packets that carry their own address.</li>
 * <li>{@code MULTICAST_SEND}: {@code MulticastSocket.send} with a time-to-live, which doesn't go through
 * {@code DatagramSocket.send}.</li>
 * <li>{@code CHANNEL_CONNECT}: {@code DatagramChannel.connect}, which also carries the JDK's own DNS client on newer
 * JDKs.</li>
 * <li>{@code CHANNEL_SEND}: {@code DatagramChannel.send}.</li>
 * </ul>
 */
public final class UdpHooks {

    public static final HookSpec SOCKET_CONNECT = new HookSpec("java.net.DatagramSocket",
            named("connect").and(takesArguments(SocketAddress.class)), UdpAdvice.class);
    public static final HookSpec SOCKET_CONNECT_ADDRESS = new HookSpec("java.net.DatagramSocket",
            named("connect").and(takesArguments(InetAddress.class, int.class)), UdpAddressAdvice.class);
    public static final HookSpec SOCKET_SEND = new HookSpec("java.net.DatagramSocket",
            named("send").and(takesArguments(DatagramPacket.class)), UdpPacketAdvice.class);
    public static final HookSpec MULTICAST_SEND = new HookSpec("java.net.MulticastSocket",
            named("send").and(takesArguments(DatagramPacket.class, byte.class)), UdpPacketAdvice.class);
    public static final HookSpec CHANNEL_CONNECT = new HookSpec("sun.nio.ch.DatagramChannelImpl",
            named("connect").and(takesArguments(SocketAddress.class)), UdpAdvice.class);
    public static final HookSpec CHANNEL_SEND = new HookSpec("sun.nio.ch.DatagramChannelImpl",
            named("send").and(takesArguments(ByteBuffer.class, SocketAddress.class)), UdpChannelSendAdvice.class);

    private UdpHooks() {
    }
}
