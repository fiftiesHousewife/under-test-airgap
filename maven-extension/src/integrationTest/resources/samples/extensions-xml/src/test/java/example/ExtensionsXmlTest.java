package example;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.Test;

public class ExtensionsXmlTest {

    @Test
    public void refusesAConnectionToAnotherMachine() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("192.0.2.1", 80), 2000);
            fail("connected: the agent isn't on this test");
        } catch (java.net.ConnectException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("refused by the airgap agent"));
        }
    }
}
