package com.xdpsx.ecommerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

@Testcontainers
class ScheduledSalePricingMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-16.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("scheduled_sale_migration");

    @Test
    void migration_ShouldAddScheduleColumnsAndEnforceInvariants() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE product_variants (" + "id BIGINT PRIMARY KEY, base_price DECIMAL(15,2) NOT NULL)");
            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            statement.execute("INSERT INTO product_variants (id, base_price) VALUES (1, 100.00)");
            statement.execute("INSERT INTO product_variants "
                    + "(id, base_price, sale_price, sale_starts_at, sale_ends_at) "
                    + "VALUES (2, 100.00, 80.00, '2026-10-01 00:00:00.000000', '2026-10-08 00:00:00.000000')");
            assertThatThrownBy(() -> statement.execute("UPDATE product_variants SET base_price = 80.00 WHERE id = 2"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute(
                            "INSERT INTO product_variants (id, base_price, sale_price, sale_starts_at) "
                                    + "VALUES (3, 100.00, 80.00, '2026-10-01 00:00:00.000000')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("INSERT INTO product_variants "
                            + "(id, base_price, sale_price, sale_starts_at, sale_ends_at) "
                            + "VALUES (4, 100.00, 100.00, '2026-10-01 00:00:00.000000', '2026-10-08 00:00:00.000000')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("INSERT INTO product_variants "
                            + "(id, base_price, sale_price, sale_starts_at, sale_ends_at) "
                            + "VALUES (5, 100.00, 80.00, '2026-10-08 00:00:00.000000', '2026-10-01 00:00:00.000000')"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
