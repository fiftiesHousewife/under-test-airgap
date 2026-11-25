package example.pitsample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Test;

// Run by PIT, once for each bug it plants, in JVMs started with the airgap agent and PIT's own agent.
@SuppressFBWarnings(value = "UNENCRYPTED_SOCKET",
        justification = "The socket aims at an address reserved for documentation, and the agent refuses it")
class DiscountTest {

    @Test
    void takesOffThePercentage() {
        assertAll(
                () -> assertEquals(90, Discount.priceAfter(100, 10)),
                () -> assertEquals(100, Discount.priceAfter(100, 0)));
    }

    // Fails if the agent isn't guarding the JVM PIT runs the tests in.
    @Test
    void theAgentRefusesCallsInTheJvmsPitStarts() {
        assertTrue(assertThrows(ConnectException.class, () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("192.0.2.51", 80), 1000);
            }
        }).getMessage().contains("refused by the airgap agent"));
    }
}
