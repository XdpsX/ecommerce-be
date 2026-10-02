package com.xdpsx.ecommerce.checkout.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

@Testcontainers(disabledWithoutDocker = true)
class CheckoutMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-21.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("checkout_migration");

    @Test
    void migration_ShouldConvertLegacyStatusAndAddCheckoutSnapshotColumns() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL)");
            statement.execute(
                    "CREATE TABLE orders ("
                            + "id BIGINT PRIMARY KEY, user_id BIGINT NULL, tracking_number VARCHAR(255) NOT NULL, "
                            + "status ENUM('PENDING','PROCESSING','SHIPPED','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING', "
                            + "address VARCHAR(255) NOT NULL, mobile_number VARCHAR(20) NOT NULL, description VARCHAR(500), "
                            + "total_amount DECIMAL(20,2), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6), delivered_at DATETIME(6))");
            statement.execute("INSERT INTO users VALUES (7, 'Buyer')");
            statement.execute(
                    "INSERT INTO orders (id, user_id, tracking_number, status, address, mobile_number, created_at) "
                            + "VALUES (1, 7, 'legacy-1', 'PENDING', 'Main Street', '0123456789', CURRENT_TIMESTAMP(6))");

            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            try (ResultSet row = statement.executeQuery(
                    "SELECT status, recipient_name, phone_number, address_line, currency FROM orders WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("status")).isEqualTo("PENDING_PAYMENT");
                assertThat(row.getString("recipient_name")).isEqualTo("Buyer");
                assertThat(row.getString("phone_number")).isEqualTo("0123456789");
                assertThat(row.getString("address_line")).isEqualTo("Main Street");
                assertThat(row.getString("currency")).isEqualTo("VND");
            }
            try (ResultSet columns = statement.executeQuery(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders'")) {
                HashSet<String> names = new HashSet<>();
                while (columns.next()) names.add(columns.getString(1));
                assertThat(names)
                        .contains(
                                "recipient_name",
                                "phone_number",
                                "address_line",
                                "ward_commune",
                                "district",
                                "province_city",
                                "postal_code",
                                "currency",
                                "idempotency_key_hash",
                                "checkout_request_hash",
                                "reservation_expires_at");
            }
        }
    }
}
