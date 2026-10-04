package com.xdpsx.ecommerce.refund.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
class FulfillmentCancellationRefundMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-24.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("fulfillment_refund_migration");

    @Test
    void migration_ShouldCreateRefundConstraintsAndCancellableAttemptStatus() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE payments (id BIGINT PRIMARY KEY, order_id BIGINT)");
            statement.execute(
                    "CREATE TABLE payment_attempts (id BIGINT PRIMARY KEY, status VARCHAR(32) NOT NULL, "
                            + "CONSTRAINT ck_payment_attempts_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED','EXPIRED')))");

            executeChangeset(statement, CHANGESET);

            statement.execute("INSERT INTO payments (id, order_id) VALUES (1, 1), (2, 2)");
            statement.execute("INSERT INTO payment_attempts (id, status) VALUES (1, 'CANCELLED')");
            statement.execute(
                    "INSERT INTO refunds (payment_id, status, amount, currency, reason, requested_by, requested_at) "
                            + "VALUES (1, 'PENDING', 100.00, 'VND', 'customer request', 'buyer@example.test', CURRENT_TIMESTAMP(6))");

            assertThatThrownBy(
                            () -> statement.execute(
                                    "INSERT INTO refunds (payment_id, status, amount, currency, reason, requested_by, requested_at) "
                                            + "VALUES (1, 'PENDING', 100.00, 'VND', 'duplicate', 'buyer@example.test', CURRENT_TIMESTAMP(6))"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(
                            () -> statement.execute(
                                    "INSERT INTO refunds (payment_id, status, amount, currency, reason, requested_by, requested_at) "
                                            + "VALUES (2, 'PENDING', 0, 'VND', 'invalid', 'buyer@example.test', CURRENT_TIMESTAMP(6))"))
                    .isInstanceOf(SQLException.class);

            try (var columns = statement.executeQuery(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders'")) {
                HashSet<String> names = new HashSet<>();
                while (columns.next()) names.add(columns.getString(1));
                assertThat(names).contains("cancellation_reason", "cancelled_by", "cancelled_at");
            }
        }
    }

    private static void executeChangeset(Statement statement, Path path) throws Exception {
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        String withoutComments = Arrays.stream(sql.split("\\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
        for (String command : withoutComments.split(";")) {
            if (!command.trim().isEmpty()) statement.execute(command.trim());
        }
    }
}
