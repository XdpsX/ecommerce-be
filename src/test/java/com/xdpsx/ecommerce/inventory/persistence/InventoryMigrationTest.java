package com.xdpsx.ecommerce.inventory.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

@Testcontainers
class InventoryMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-12.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("inventory_migration");

    @Test
    void migration_ShouldBackfillBalancesAndEnforceInventoryInvariants() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE product_variants (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO product_variants (id) VALUES (10)");
            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            try (ResultSet result =
                    statement.executeQuery("SELECT on_hand, reserved FROM inventory_balances WHERE variant_id = 10")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("on_hand")).isZero();
                assertThat(result.getLong("reserved")).isZero();
            }
            assertThatThrownBy(
                            () -> statement.execute("UPDATE inventory_balances SET on_hand = -1 WHERE variant_id = 10"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_inventory_balance_on_hand_non_negative");
            assertThatThrownBy(() -> statement.execute(
                            "UPDATE inventory_balances SET on_hand = 1, reserved = 2 WHERE variant_id = 10"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_inventory_balance_reserved_not_above_on_hand");
            assertThatThrownBy(() -> statement.execute(
                            "INSERT INTO inventory_balances (variant_id, on_hand, reserved) VALUES (10, 0, 0)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
