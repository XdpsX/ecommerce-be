package com.xdpsx.ecommerce.cart.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

/** Verifies that the Cart foundation migration preserves legacy customer rows. */
@Testcontainers(disabledWithoutDocker = true)
class CartMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-20.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("cart_migration");

    @Test
    void migration_ShouldCreateCustomerCartsAndPreserveQuantities() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE product_variants (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE cart_items (user_id BIGINT NOT NULL, variant_id BIGINT NOT NULL, quantity INT NOT NULL, "
                            + "created_at TIMESTAMP NULL, updated_at TIMESTAMP NULL, PRIMARY KEY (user_id, variant_id), "
                            + "CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES users(id), "
                            + "CONSTRAINT fk_cart_item_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id), "
                            + "INDEX ix_cart_item_variant (variant_id))");
            statement.execute("INSERT INTO users VALUES (7), (8)");
            statement.execute("INSERT INTO product_variants VALUES (101), (102), (103)");
            statement.execute(
                    "INSERT INTO cart_items (user_id, variant_id, quantity) VALUES (7, 101, 2), (7, 102, 4), (8, 103, 100)");

            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM carts")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            try (ResultSet indexes = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                            + "AND table_name = 'cart_items' AND index_name = 'ix_cart_item_variant'")) {
                assertThat(indexes.next()).isTrue();
                assertThat(indexes.getInt(1)).isEqualTo(1);
            }
            try (ResultSet items = statement.executeQuery(
                    "SELECT c.user_id, ci.variant_id, ci.quantity FROM cart_items ci JOIN carts c ON c.id = ci.cart_id ORDER BY c.user_id, ci.variant_id")) {
                assertThat(items.next()).isTrue();
                assertThat(items.getLong("user_id")).isEqualTo(7);
                assertThat(items.getLong("variant_id")).isEqualTo(101);
                assertThat(items.getInt("quantity")).isEqualTo(2);
                assertThat(items.next()).isTrue();
                assertThat(items.getInt("quantity")).isEqualTo(4);
                assertThat(items.next()).isTrue();
                assertThat(items.getLong("user_id")).isEqualTo(8);
                assertThat(items.getInt("quantity")).isEqualTo(99);
                assertThat(items.next()).isFalse();
            }
        }
    }
}
