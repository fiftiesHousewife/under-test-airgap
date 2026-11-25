package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.AsyncConnectAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.AsyncConnectHandlerAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.HandOverAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.HandOverExecutorAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.JdkClientProxyAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.ReachabilityAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.SctpSendAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.TcpAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.UrlConnectionProxyAdvice;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * The TCP hooks, and those that carry a request's test to the JDK HTTP client's threads.
 *
 * <ul>
 * <li>{@code SOCKET}: every plain socket connect, which all of {@code Socket}'s constructors and {@code connect}
 * methods reach.</li>
 * <li>{@code SOCKET_ADAPTOR}: the socket a {@code SocketChannel} hands out: from JDK 13 its {@code connect} goes
 * straight to the channel's blocking connect, past both the channel's and {@code Socket}'s.</li>
 * <li>{@code REACHABILITY}: {@code InetAddress.isReachable}, which sends ICMP or TCP echo packets to the address.</li>
 * <li>{@code SCTP_CONNECT}, {@code SCTP_SEND}: SCTP channels' connect, where the JDK supports SCTP (Linux), and SCTP
 * multi-channels' send, which makes an association with the address the message names.</li>
 * <li>{@code CHANNEL}: NIO socket channels, used by Netty, the JDK's HTTP client and many drivers.</li>
 * <li>{@code ASYNC}, {@code ASYNC_HANDLER}: asynchronous socket channels' {@code connect} that returns a future, and
 * the one that calls a handler.</li>
 * <li>{@code HTTP_CLIENT_TASKS}: the JDK HTTP client's executor, from JDK 11: its tasks carry the test and caller of
 * the thread that handed them over, so calls made on the client's own threads are allowed and named as the request's
 * sender. Refuses nothing itself; the client's connects are refused by the channel hooks.</li>
 * <li>{@code HTTP_CLIENT_SEND}: the JDK HTTP client's private {@code sendAsync}, which every request reaches, from
 * JDK 11: the executor it is given runs the request's tasks with the sender's test and caller.</li>
 * <li>{@code URL_CONNECTION_PROXY}, {@code HTTP_CLIENT_PROXY}: where {@code HttpURLConnection} opens its server
 * ({@code sun.net.www.http.HttpClient.openServer()}) and where the JDK HTTP client sends each request (its private
 * {@code sendAsync}): through an HTTP proxy, the destination is checked as well as the proxy. They check only behind
 * a proxy, so a route through them isn't guarded by them; a direct connection is checked by the socket hooks.</li>
 * </ul>
 */
public final class TcpHooks {

    public static final HookSpec SOCKET = new HookSpec("java.net.Socket",
            named("connect").and(takesArguments(SocketAddress.class, int.class)), TcpAdvice.class);
    public static final HookSpec SOCKET_ADAPTOR = new HookSpec("sun.nio.ch.SocketAdaptor",
            named("connect").and(takesArguments(SocketAddress.class, int.class)), TcpAdvice.class);
    public static final HookSpec REACHABILITY = new HookSpec("java.net.InetAddress",
            named("isReachable").and(takesArguments(NetworkInterface.class, int.class, int.class)),
            ReachabilityAdvice.class);
    public static final HookSpec SCTP_CONNECT = new HookSpec("sun.nio.ch.sctp.SctpChannelImpl",
            named("connect").and(takesArguments(SocketAddress.class)), TcpAdvice.class);
    public static final HookSpec SCTP_SEND = new HookSpec("sun.nio.ch.sctp.SctpMultiChannelImpl",
            named("send").and(takesArguments(2)).and(takesArgument(0, ByteBuffer.class)), SctpSendAdvice.class);
    public static final HookSpec CHANNEL = new HookSpec("sun.nio.ch.SocketChannelImpl",
            named("connect").and(takesArguments(SocketAddress.class)), TcpAdvice.class);
    public static final HookSpec ASYNC = new HookSpec("sun.nio.ch.AsynchronousSocketChannelImpl",
            named("connect").and(takesArguments(SocketAddress.class)), AsyncConnectAdvice.class);
    public static final HookSpec HTTP_CLIENT_TASKS = new HookSpec(
            "jdk.internal.net.http.HttpClientImpl$DelegatingExecutor",
            named("execute").and(takesArguments(Runnable.class)), HandOverAdvice.class);
    public static final HookSpec HTTP_CLIENT_SEND = new HookSpec("jdk.internal.net.http.HttpClientImpl",
            named("sendAsync").and(isPrivate()).and(takesArguments(4)), HandOverExecutorAdvice.class);
    public static final HookSpec ASYNC_HANDLER = new HookSpec("sun.nio.ch.AsynchronousSocketChannelImpl",
            named("connect").and(takesArguments(3)).and(takesArgument(0, SocketAddress.class)),
            AsyncConnectHandlerAdvice.class);

    public static final HookSpec URL_CONNECTION_PROXY = new HookSpec("sun.net.www.http.HttpClient",
            named("openServer").and(takesArguments(0)), UrlConnectionProxyAdvice.class);
    public static final HookSpec HTTP_CLIENT_PROXY = new HookSpec("jdk.internal.net.http.HttpClientImpl",
            named("sendAsync").and(isPrivate()).and(takesArguments(4)), JdkClientProxyAdvice.class);

    private TcpHooks() {
    }
}
