package example.orders;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Charges a payment through the payments service's HTTP API.
@Component
public class Payments {

    private final String url;

    public Payments(@Value("${payments.url}") final String url) {
        this.url = url;
    }

    public int charge(final String order) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url + "?order=" + order).openConnection();
        connection.setRequestMethod("POST");
        return connection.getResponseCode();
    }
}
