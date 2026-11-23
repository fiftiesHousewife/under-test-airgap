package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.airgapexample.NativeLoader;
import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.io.File;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.naming.CommunicationException;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Every hooked kind of call, made for real with the agent attached, is refused at once with the exception an
 * unplugged machine would throw. Every target is either a name that can't resolve or an address reserved for
 * documentation, so nothing could leave the machine even if a hook failed; and every test has a timeout well short of
 * a network timeout, so a refusal that didn't happen shows as a failure, not a slow pass. It allows for a cold start:
 * the first JNDI call alone takes around half a second.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = {"UNENCRYPTED_SOCKET", "DM_EXIT"},
        justification = "Plain connections and exits are what the agent must refuse")
class RefusedCallsTest {

    private static final String UNRESOLVABLE = "db.prod.example.invalid";
    private static final String DOCUMENTATION_ADDRESS = "192.0.2.1";

    @Test
    void refusesHostLookups() {
        assertAll(
                () -> assertThatThrownBy(() -> InetAddress.getByName(UNRESOLVABLE))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> InetAddress.getAllByName(UNRESOLVABLE))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> new Socket(UNRESOLVABLE, 5432))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesTcpThroughEveryRoute() {
        final InetSocketAddress remote = new InetSocketAddress(DOCUMENTATION_ADDRESS, 5432);
        assertAll(
                () -> assertThatThrownBy(() -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(remote, 1000);
                    }
                }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> SocketChannel.open(remote).close())
                        .isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
                        channel.connect(remote).get(5, TimeUnit.SECONDS);
                    }
                }).as("a failed future, as connect declares no checked exception")
                        .isInstanceOf(ExecutionException.class).cause()
                        .isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesHttpThroughUrlConnections() {
        assertThatThrownBy(() -> {
            final HttpURLConnection connection = (HttpURLConnection)
                    URI.create("http://" + DOCUMENTATION_ADDRESS + ":8080/orders").toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.connect();
        }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED);
    }

    @Test
    void refusesUdpPacketsAndConnects() {
        final InetSocketAddress remote = new InetSocketAddress(DOCUMENTATION_ADDRESS, 53);
        assertAll(
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.send(new DatagramPacket(new byte[1], 1, remote));
                    }
                }).isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.connect(remote.getAddress(), 53);
                    }
                }).as("unchecked, as this connect declares no checked exception")
                        .isInstanceOf(UncheckedIOException.class).cause()
                        .isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.connect(InetSocketAddress.createUnresolved(UNRESOLVABLE, 53));
                    }
                }).as("as the JDK refuses an address nobody looked up")
                        .isExactlyInstanceOf(SocketException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (DatagramChannel channel = DatagramChannel.open()) {
                        channel.send(ByteBuffer.allocate(1), remote);
                    }
                }).isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesStartingPrograms() {
        assertAll(
                () -> assertThatThrownBy(() -> new ProcessBuilder("sh", "-c", "exit 0").start())
                        .isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> Runtime.getRuntime().exec(new String[] {"sh", "-c", "echo"}))
                        .isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesNamesThatOnlyLookLikeAddresses() {
        assertAll(
                () -> assertThatThrownBy(() -> InetAddress.getByName("127.orders.example.invalid"))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> InetAddress.getByName("999.0.2.1"))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED));
    }

    /** The old multicast send with a time-to-live: deprecated, so called by reflection, but in every supported JDK. */
    @Test
    void refusesMulticastPacketsSentWithATimeToLive() throws IOException, NoSuchMethodException {
        final Method send = MulticastSocket.class.getMethod("send", DatagramPacket.class, byte.class);
        try (MulticastSocket socket = new MulticastSocket()) {
            final DatagramPacket packet = new DatagramPacket(new byte[1], 1,
                    new InetSocketAddress(DOCUMENTATION_ADDRESS, 53));
            assertThatThrownBy(() -> send.invoke(socket, packet, (byte) 1))
                    .isInstanceOf(InvocationTargetException.class).cause()
                    .isInstanceOf(PortUnreachableException.class).hasMessageContaining(Refusals.REFUSED);
        }
    }

    @Test
    void refusesProgramsStartedAsAPipeline() {
        final Method startPipeline = pipelineMethod();
        assumeTrue(startPipeline != null, "ProcessBuilder.startPipeline came in JDK 9");
        final List<ProcessBuilder> pipeline = Arrays.asList(
                new ProcessBuilder("sh", "-c", "exit 0"), new ProcessBuilder("sh", "-c", "cat"));

        assertThatThrownBy(() -> startPipeline.invoke(null, pipeline))
                .isInstanceOf(InvocationTargetException.class).cause()
                .isInstanceOf(IOException.class).hasMessageContaining(Refusals.REFUSED);
    }

    private static Method pipelineMethod() {
        try {
            return ProcessBuilder.class.getMethod("startPipeline", List.class);
        } catch (NoSuchMethodException beforeJdk9) {
            return null;
        }
    }

    @Test
    void allowsWhatTheSettingsAndTheSettingsFileAllowWithoutMakingTheCall() {
        assertAll(
                () -> assertThat(Gate.isAllowed("allowed.example.invalid", 8443)).isTrue(),
                () -> assertThat(Gate.isAllowed("allowed.example.invalid", 9999)).isFalse(),
                () -> assertThat(Gate.isAllowed("settings-file.example.invalid", 8443)).as("from the settings file")
                        .isTrue(),
                () -> assertThat(Gate.isAllowed(UNRESOLVABLE, 5432)).isFalse(),
                () -> assertThat(Gate.isAllowed("localhost", 5432)).isTrue());
    }

    @Test
    void refusesJndiLookupsOfOtherMachines() {
        assertAll(
                () -> assertThatThrownBy(() -> new InitialContext().lookup("ldap://" + UNRESOLVABLE + "/o=orders"))
                        .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> new InitialContext().lookup(
                        new CompositeName("rmi://" + DOCUMENTATION_ADDRESS + ":1099/orders")))
                        .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> new InitialDirContext().getAttributes(
                        "dns://" + DOCUMENTATION_ADDRESS + "/example.com"))
                        .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesADirectoryNamedInTheJndiSettings() {
        final Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://" + UNRESOLVABLE + ":389/dc=example,dc=com");

        assertThatThrownBy(() -> new InitialDirContext(environment))
                .isInstanceOf(CommunicationException.class).hasMessageContaining(Refusals.REFUSED);
    }

    @Test
    void leavesJndiNamesInsideTheJvmAlone() {
        assertThatThrownBy(() -> new InitialContext().lookup("java:comp/env/jdbc/orders"))
                .isInstanceOf(NamingException.class).hasMessageNotContaining(Refusals.REFUSED);
    }

    @Test
    void refusesLoadingNativeLibraries() {
        final String path = new File(System.getProperty("java.io.tmpdir"), "libairgap-example.so").getAbsolutePath();
        assertAll(
                () -> assertThatThrownBy(() -> System.loadLibrary("airgapexample"))
                        .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> System.load(path))
                        .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> Runtime.getRuntime().loadLibrary("airgapexample"))
                        .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void refusesNativeLibrariesHoweverTheyAreAskedFor() throws NoSuchMethodException {
        final Method loadLibrary = System.class.getMethod("loadLibrary", String.class);
        assertAll(
                () -> assertThatThrownBy(() -> NativeLoader.load("airgapexample"))
                        .as("a class path class in a package like the JDK's")
                        .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> loadLibrary.invoke(null, "airgapexample"))
                        .as("through reflection")
                        .isInstanceOf(InvocationTargetException.class).cause()
                        .isInstanceOf(UnsatisfiedLinkError.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void letsAnAllowedNativeLibraryLoad() {
        assertThatThrownBy(() -> System.loadLibrary("airgapallowed"))
                .as("allowed, so it fails only because the library isn't there")
                .isInstanceOf(UnsatisfiedLinkError.class).hasMessageNotContaining(Refusals.REFUSED);
    }

    @Test
    void refusesEndingTheJvmDuringATest() {
        Gate.enterTest(RefusedCallsTest.class.getName() + ".refusesEndingTheJvmDuringATest");
        try {
            assertAll(
                    () -> assertThatThrownBy(() -> System.exit(3))
                            .isInstanceOf(SecurityException.class).hasMessageContaining(Refusals.REFUSED),
                    () -> assertThatThrownBy(() -> Runtime.getRuntime().halt(3))
                            .isInstanceOf(SecurityException.class).hasMessageContaining(Refusals.REFUSED));
        } finally {
            Gate.leaveTest();
        }
    }
}
