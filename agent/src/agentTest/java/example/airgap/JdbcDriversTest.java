package example.airgap;

import static example.airgap.ClientCalls.assertCallerIs;
import static example.airgap.ClientCalls.assertRefused;
import static example.airgap.ClientCalls.assertReported;
import static example.airgap.ClientCalls.failureOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// The PostgreSQL and MySQL JDBC drivers: each one's connection is refused, fails as it would offline, and is reported
// with the right kind, on every supported JDK.
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class JdbcDriversTest {

    @Test
    void postgresqlDriver() {
        final Throwable byAddress = failureOf(() -> DriverManager.getConnection(
                "jdbc:postgresql://192.0.2.35:5432/orders").close());
        final Throwable byName = failureOf(() -> DriverManager.getConnection(
                "jdbc:postgresql://db.example.invalid:5432/orders").close());

        assertAll(
                () -> assertThat(byAddress).isInstanceOf(SQLException.class),
                () -> assertRefused(byAddress, ConnectException.class),
                () -> assertReported("192.0.2.35:5432", "tcp"),
                () -> assertRefused(byName, UnknownHostException.class),
                () -> assertReported("db.example.invalid", "host-lookup"));
    }

    @Test
    void mysqlDriver() {
        final Throwable failure = failureOf(() -> DriverManager.getConnection(
                "jdbc:mysql://192.0.2.36:3306/orders").close());

        assertAll(
                () -> assertThat(failure).isInstanceOf(SQLException.class),
                () -> assertRefused(failure, ConnectException.class),
                () -> assertReported("192.0.2.36:3306", "tcp"),
                () -> assertCallerIs(JdbcDriversTest.class, "192.0.2.36:3306"));
    }
}
