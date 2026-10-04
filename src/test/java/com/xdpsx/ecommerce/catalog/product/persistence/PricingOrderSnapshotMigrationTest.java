package com.xdpsx.ecommerce.catalog.product.persistence;

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

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

/** Verifies the destructive CR2 schema transition and snapshot constraints against MySQL. */
@Testcontainers
class PricingOrderSnapshotMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-15.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("pricing_order_snapshot_migration");

    @Test
    void migration_ShouldClearLegacyRowsAndCreateVariantCartAndSnapshotColumns() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE products (id BIGINT PRIMARY KEY, price DECIMAL(15,2) NOT NULL, discount_percent DECIMAL(3,1))");
            statement.execute("CREATE TABLE product_variants (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT)");
            statement.execute(
                    "CREATE TABLE payments (id BIGINT PRIMARY KEY, order_id BIGINT, FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL)");
            statement.execute(
                    "CREATE TABLE cart_items (user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL, PRIMARY KEY (user_id, product_id), "
                            + "CONSTRAINT cart_items_ibfk_1 FOREIGN KEY (user_id) REFERENCES users(id), CONSTRAINT cart_items_ibfk_2 FOREIGN KEY (product_id) REFERENCES products(id))");
            statement.execute(
                    "CREATE TABLE order_items (id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT, product_id BIGINT, quantity INT, "
                            + "CONSTRAINT order_items_order_fk FOREIGN KEY (order_id) REFERENCES orders(id), CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products(id))");
            statement.execute("INSERT INTO users VALUES (1)");
            statement.execute("INSERT INTO products VALUES (1, 10.00, 0)");
            statement.execute("INSERT INTO orders VALUES (1, 1)");
            statement.execute("INSERT INTO payments VALUES (1, 1)");
            statement.execute("INSERT INTO cart_items VALUES (1, 1, 1)");
            statement.execute("INSERT INTO order_items (order_id, product_id, quantity) VALUES (1, 1, 1)");

            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            try (ResultSet rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM payments) payments, "
                    + "(SELECT COUNT(*) FROM orders) orders, (SELECT COUNT(*) FROM cart_items) cart_items")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("payments")).isZero();
                assertThat(rows.getInt("orders")).isZero();
                assertThat(rows.getInt("cart_items")).isZero();
            }
            try (ResultSet columns = statement.executeQuery("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'order_items'")) {
                HashSet<String> names = new HashSet<>();
                while (columns.next()) names.add(columns.getString(1));
                assertThat(names)
                        .contains(
                                "product_id",
                                "product_name",
                                "variant_id",
                                "sku",
                                "variant_description",
                                "unit_base_price",
                                "discount_amount",
                                "final_unit_price",
                                "subtotal",
                                "currency");
            }
            try (ResultSet orderNullability =
                    statement.executeQuery("SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'order_items' "
                            + "AND column_name IN ('product_id', 'product_name', 'variant_id', 'sku', 'variant_description', "
                            + "'unit_base_price', 'discount_amount', 'final_unit_price', 'subtotal', 'currency', 'quantity') "
                            + "AND is_nullable = 'NO'")) {
                HashSet<String> names = new HashSet<>();
                while (orderNullability.next()) names.add(orderNullability.getString(1));
                assertThat(names)
                        .contains(
                                "product_id",
                                "product_name",
                                "variant_id",
                                "sku",
                                "variant_description",
                                "unit_base_price",
                                "discount_amount",
                                "final_unit_price",
                                "subtotal",
                                "currency",
                                "quantity");
            }
            try (ResultSet cartColumns = statement.executeQuery("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'cart_items'")) {
                HashSet<String> names = new HashSet<>();
                while (cartColumns.next()) names.add(cartColumns.getString(1));
                assertThat(names).contains("user_id", "variant_id", "quantity").doesNotContain("product_id");
            }
            try (ResultSet cartKey =
                    statement.executeQuery("SELECT column_name FROM information_schema.key_column_usage "
                            + "WHERE table_schema = DATABASE() AND table_name = 'cart_items' "
                            + "AND constraint_name = 'PRIMARY' ORDER BY ordinal_position")) {
                assertThat(cartKey.next()).isTrue();
                assertThat(cartKey.getString(1)).isEqualTo("user_id");
                assertThat(cartKey.next()).isTrue();
                assertThat(cartKey.getString(1)).isEqualTo("variant_id");
            }
            try (ResultSet cartForeignKey =
                    statement.executeQuery("SELECT referenced_table_name, referenced_column_name "
                            + "FROM information_schema.key_column_usage WHERE table_schema = DATABASE() "
                            + "AND table_name = 'cart_items' AND constraint_name = 'fk_cart_item_variant'")) {
                assertThat(cartForeignKey.next()).isTrue();
                assertThat(cartForeignKey.getString("referenced_table_name")).isEqualTo("product_variants");
                assertThat(cartForeignKey.getString("referenced_column_name")).isEqualTo("id");
            }
            try (ResultSet indexes =
                    statement.executeQuery("SELECT DISTINCT index_name FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND ((table_name = 'cart_items' AND index_name = 'ix_cart_item_variant') "
                            + "OR (table_name = 'order_items' AND index_name = 'ix_order_item_order'))")) {
                HashSet<String> names = new HashSet<>();
                while (indexes.next()) names.add(indexes.getString(1));
                assertThat(names).contains("ix_cart_item_variant", "ix_order_item_order");
            }
            try (ResultSet productColumns = statement.executeQuery("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'products'")) {
                HashSet<String> names = new HashSet<>();
                while (productColumns.next()) names.add(productColumns.getString(1));
                assertThat(names).doesNotContain("price", "discount_percent");
            }
        }
    }
}
