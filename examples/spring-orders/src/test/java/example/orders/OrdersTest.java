package example.orders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// The service's own tests, as written before anyone thought about the network: under the agent, each call to the
// real systems fails as it would on an unplugged machine, and the report names the setting behind it.
@SpringBootTest
class OrdersTest {

    @Autowired
    private Payments payments;

    @Autowired
    private StockFeed stock;

    @Test
    void chargesAPayment() {
        assertThatThrownBy(() -> payments.charge("A-1")).isInstanceOf(IOException.class);
    }

    @Test
    void checksTheStock() {
        assertThatThrownBy(() -> stock.inStock("widget")).isInstanceOf(IOException.class);
    }
}
