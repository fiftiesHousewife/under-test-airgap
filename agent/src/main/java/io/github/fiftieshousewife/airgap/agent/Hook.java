package io.github.fiftieshousewife.airgap.agent;

import io.github.fiftieshousewife.airgap.agent.hooks.FileHooks;
import io.github.fiftieshousewife.airgap.agent.hooks.HookMethods;
import io.github.fiftieshousewife.airgap.agent.hooks.HookSpec;
import io.github.fiftieshousewife.airgap.agent.hooks.JndiHooks;
import io.github.fiftieshousewife.airgap.agent.hooks.LookupHooks;
import io.github.fiftieshousewife.airgap.agent.hooks.NativeHooks;
import io.github.fiftieshousewife.airgap.agent.hooks.ProcessHooks;
import io.github.fiftieshousewife.airgap.agent.hooks.TcpHooks;
import io.github.fiftieshousewife.airgap.agent.hooks.UdpHooks;
import java.util.Locale;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Every hook: the JDK class and methods it rewrites, and the advice it adds. Each is the lowest point in the JDK that
 * every route to that kind of call goes through, on JDK 8 and every version since; {@link SelfCheck} proves each one
 * at start. What each rewrites, and why there, is on its table in {@code hooks}: {@link LookupHooks},
 * {@link TcpHooks}, {@link UdpHooks}, {@link ProcessHooks}, {@link JndiHooks}, {@link NativeHooks} and
 * {@link FileHooks}. A hook's name starts with the {@link Kind} of call it catches.
 */
public enum Hook {

    HOST_LOOKUP_ALL(LookupHooks.ALL),
    HOST_LOOKUP_OWN_NAME(LookupHooks.OWN_NAME),
    HOST_LOOKUP_REVERSE(LookupHooks.REVERSE),
    TCP_SOCKET(TcpHooks.SOCKET),
    TCP_SOCKET_ADAPTOR(TcpHooks.SOCKET_ADAPTOR),
    TCP_REACHABILITY(TcpHooks.REACHABILITY),
    TCP_SCTP_CONNECT(TcpHooks.SCTP_CONNECT),
    TCP_SCTP_SEND(TcpHooks.SCTP_SEND),
    TCP_CHANNEL(TcpHooks.CHANNEL),
    TCP_ASYNC(TcpHooks.ASYNC),
    TCP_HTTP_CLIENT_TASKS(TcpHooks.HTTP_CLIENT_TASKS),
    TCP_HTTP_CLIENT_SEND(TcpHooks.HTTP_CLIENT_SEND),
    TCP_ASYNC_HANDLER(TcpHooks.ASYNC_HANDLER),
    TCP_URL_CONNECTION_PROXY(TcpHooks.URL_CONNECTION_PROXY),
    TCP_HTTP_CLIENT_PROXY(TcpHooks.HTTP_CLIENT_PROXY),
    UDP_SOCKET_CONNECT(UdpHooks.SOCKET_CONNECT),
    UDP_SOCKET_CONNECT_ADDRESS(UdpHooks.SOCKET_CONNECT_ADDRESS),
    UDP_SOCKET_SEND(UdpHooks.SOCKET_SEND),
    UDP_MULTICAST_SEND(UdpHooks.MULTICAST_SEND),
    UDP_CHANNEL_CONNECT(UdpHooks.CHANNEL_CONNECT),
    UDP_CHANNEL_SEND(UdpHooks.CHANNEL_SEND),
    PROCESS(ProcessHooks.START),
    JNDI_NAME(JndiHooks.NAME),
    JNDI_COMPOSITE_NAME(JndiHooks.COMPOSITE_NAME),
    JNDI_PROVIDER(JndiHooks.PROVIDER),
    NATIVE_LIBRARY_BY_NAME(NativeHooks.BY_NAME),
    NATIVE_LIBRARY_BY_PATH(NativeHooks.BY_PATH),
    NATIVE_LIBRARY_RUNTIME_BY_NAME(NativeHooks.RUNTIME_BY_NAME),
    NATIVE_LIBRARY_RUNTIME_BY_PATH(NativeHooks.RUNTIME_BY_PATH),
    NATIVE_LIBRARY_FOREIGN_BY_NAME(NativeHooks.FOREIGN_BY_NAME),
    NATIVE_LIBRARY_FOREIGN_BY_PATH(NativeHooks.FOREIGN_BY_PATH),
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
    EXIT(ProcessHooks.EXIT),
    HALT(ProcessHooks.HALT);

    private final String typeName;
    private final ElementMatcher<? super MethodDescription> methods;
    private final Class<?> advice;

    Hook(final HookSpec spec) {
        this.typeName = spec.getTypeName();
        this.methods = spec.getMethods();
        this.advice = spec.getAdvice();
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
        return !HookMethods.HOOKS_ONLY_SOME_JDKS_HAVE.contains(name()) || HookMethods.declares(typeName, "");
    }

    /**
     * Whether the hook refuses every call that passes it, and so guards each route through it. The HTTP client's
     * hand-overs pass on the sender's test and caller, the secure directory stream's constructor notes its folder,
     * and the proxy hooks check only calls made through a proxy; a route through them is guarded elsewhere.
     *
     * @return false for those hooks
     */
    public boolean refuses() {
        return !HookMethods.HOOKS_THAT_GUARD_NO_ROUTE.contains(name());
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
