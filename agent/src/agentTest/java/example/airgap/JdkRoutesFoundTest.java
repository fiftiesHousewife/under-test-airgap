package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketPermission;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The routes round the hooks that reading the JDK's own classes found (see JdkRoutesTest), each now refused. Every
 * target is an address reserved for documentation or a name that can't resolve.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class JdkRoutesFoundTest {

    private static List<String> linesAbout(final String target) throws IOException {
        return Files.readAllLines(Paths.get(System.getProperty("airgap.test.report")), StandardCharsets.UTF_8)
                .stream().filter(line -> line.contains("\"target\":\"" + target)).collect(Collectors.toList());
    }

    /** @return the running Java's feature version: 8 for {@code 1.8}, 25 for {@code 25} */
    private static int javaVersion() {
        final String version = System.getProperty("java.specification.version");
        return Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
    }

    /** From JDK 13 the socket a channel hands out connects past both the channel's and Socket's own connect. */
    @Test
    void refusesTheSocketAChannelHandsOut() {
        final InetSocketAddress remote = new InetSocketAddress("192.0.2.1", 5432);
        assertAll(
                () -> assertThatThrownBy(() -> {
                    try (SocketChannel channel = SocketChannel.open()) {
                        channel.socket().connect(remote, 1000);
                    }
                }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> {
                    try (SocketChannel channel = SocketChannel.open(); Socket socket = channel.socket()) {
                        socket.connect(remote);
                    }
                }).isInstanceOf(ConnectException.class).hasMessageContaining(Refusals.REFUSED));
    }

    @Test
    void answersThatAnotherMachineCantBeReached() throws IOException {
        final InetAddress remote = InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 11});

        assertAll(
                () -> assertThat(remote.isReachable(1000)).isFalse(),
                () -> assertThat(linesAbout("192.0.2.11:7")).singleElement().asString()
                        .contains("\"kind\":\"tcp\"", "\"outcome\":\"refused\""));
    }

    /** SocketPermission looks names up itself to compare them, past the public lookups. */
    @Test
    void refusesTheLookupsTheJdkMakesToComparePermissions() throws IOException {
        final boolean implied = new SocketPermission("perm-a.example.invalid", "connect")
                .implies(new SocketPermission("perm-b.example.invalid", "connect"));

        assertAll(
                () -> assertThat(implied).isFalse(),
                () -> assertThat(linesAbout("perm-a.example.invalid")).isNotEmpty()
                        .allSatisfy(line -> assertThat(line).contains("\"kind\":\"host-lookup\"",
                                "\"outcome\":\"refused\"")));
    }

    /** The machine's own name is local: looking it up goes ahead, as before. */
    @Test
    void letsTheMachineLookUpItsOwnName() {
        final Throwable thrown = catchThrowable(InetAddress::getLocalHost);

        assertThat(thrown == null ? "" : String.valueOf(thrown.getMessage())).doesNotContain(Refusals.REFUSED);
    }

    /** JDK 22's foreign function API loads libraries without System.loadLibrary. */
    @Test
    void refusesLibrariesTheForeignFunctionApiLoads() throws ReflectiveOperationException {
        assumeTrue(javaVersion() >= 22, "the foreign function API is final from JDK 22");
        final Class<?> arena = Class.forName("java.lang.foreign.Arena");
        final Object global = arena.getMethod("global").invoke(null);
        final Class<?> lookup = Class.forName("java.lang.foreign.SymbolLookup");
        final Method byName = lookup.getMethod("libraryLookup", String.class, arena);
        final Method byPath = lookup.getMethod("libraryLookup", java.nio.file.Path.class, arena);
        final java.nio.file.Path library = Paths.get(System.getProperty("java.io.tmpdir"), "libairgapexample.so");

        assertAll(
                () -> assertThatThrownBy(() -> byName.invoke(null, "airgapexample", global))
                        .isInstanceOf(InvocationTargetException.class).cause()
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> byPath.invoke(null, library, global))
                        .isInstanceOf(InvocationTargetException.class).cause()
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(Refusals.REFUSED));
    }
}
