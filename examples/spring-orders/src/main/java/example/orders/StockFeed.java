package example.orders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Reads stock levels from the warehouse's feed, over a plain socket.
@Component
public class StockFeed {

    private final String host;
    private final int port;

    public StockFeed(@Value("${stock.host}") final String host, @Value("${stock.port}") final int port) {
        this.host = host;
        this.port = port;
    }

    public boolean inStock(final String item) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            socket.getOutputStream().write(item.getBytes("UTF-8"));
            return socket.getInputStream().read() == 'Y';
        }
    }
}
