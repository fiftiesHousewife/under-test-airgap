package example.airgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.airgapexample.NativeLoader;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.fiftieshousewife.airgap.agent.Gate;
import io.github.fiftieshousewife.airgap.agent.Refusals;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Every hooked kind of call, made for real with the agent attached, is refused at once with the exception an
// unplugged machine would throw: host lookups, programs, native libraries and ending the JVM here, connections in
// RefusedNetworkCallsTest and JNDI in RefusedJndiCallsTest. Every target is either a name that can't resolve or an
// address reserved for documentation, so nothing could leave the machine even if a hook failed; and every test has a
// timeout well short of a network timeout, so a refusal that didn't happen shows as a failure, not a slow pass.
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@SuppressFBWarnings(value = {"UNENCRYPTED_SOCKET", "DM_EXIT"},
        justification = "Plain connections and exits are what the agent must refuse")
class RefusedCallsTest {

    private static final String UNRESOLVABLE = "db.prod.example.invalid";

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
    void refusesNamesThatOnlyLookLikeAddresses() {
        assertAll(
                () -> assertThatThrownBy(() -> InetAddress.getByName("127.orders.example.invalid"))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED),
                () -> assertThatThrownBy(() -> InetAddress.getByName("999.0.2.1"))
                        .isInstanceOf(UnknownHostException.class).hasMessageContaining(Refusals.REFUSED));
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
