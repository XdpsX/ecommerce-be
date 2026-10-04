package com.xdpsx.ecommerce.payment.persistence;

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
class PaymentMigrationTest {
    private static final Path PAYMENT_CHANGESET =
            Path.of("src/main/resources/db/changelog/changesets/changeset-23.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("payment_migration");

    @Test
    void migration_ShouldCreateAttemptTableAndMigratePaymentSummaryStatus() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, status VARCHAR(32) NOT NULL)");
            statement.execute(
                    "CREATE TABLE payments (id BIGINT PRIMARY KEY, order_id BIGINT, status ENUM('UNPAID','PAID') NOT NULL)");
            statement.execute(
                    "INSERT INTO orders VALUES (1, 'CONFIRMED'), (2, 'PAYMENT_EXPIRED'), (3, 'PENDING_PAYMENT')");
            statement.execute("INSERT INTO payments VALUES (1, 1, 'PAID'), (2, 2, 'UNPAID'), (3, 3, 'UNPAID')");

            executeChangeset(statement, PAYMENT_CHANGESET);

            try (ResultSet rows = statement.executeQuery("SELECT id, status FROM payments ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("PAID");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("EXPIRED");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("PENDING");
            }

            try (ResultSet columns = statement.executeQuery(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'payment_attempts'")) {
                HashSet<String> names = new HashSet<>();
                while (columns.next()) names.add(columns.getString(1));
                assertThat(names)
                        .contains(
                                "payment_id",
                                "provider_reference",
                                "provider_transaction_id",
                                "status",
                                "expected_amount",
                                "currency",
                                "response_code",
                                "created_at",
                                "expires_at",
                                "completed_at");
            }
            try (ResultSet indexes = statement.executeQuery(
                    "SELECT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_attempts'")) {
                HashSet<String> names = new HashSet<>();
                while (indexes.next()) names.add(indexes.getString(1));
                assertThat(names)
                        .contains(
                                "uk_payment_attempts_provider_reference",
                                "uk_payment_attempts_provider_transaction_id",
                                "ix_payment_attempts_payment_status_expiry");
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
