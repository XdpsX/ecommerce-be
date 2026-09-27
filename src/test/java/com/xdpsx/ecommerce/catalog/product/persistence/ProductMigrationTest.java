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

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Verifies the Product image and OrderItem foreign-key migration against MySQL. */
@Testcontainers
class ProductMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-9.sql");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("product_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migration_ShouldReplaceLegacyImageColumnsAndRestrictOrderDeletion() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE media (id VARCHAR(36) PRIMARY KEY)");
            statement.execute("CREATE TABLE products (id BIGINT PRIMARY KEY, main_image VARCHAR(255))");
            statement.execute(
                    "CREATE TABLE product_images (id BIGINT PRIMARY KEY AUTO_INCREMENT, url VARCHAR(255) NOT NULL, "
                            + "product_id BIGINT, FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE order_items (id BIGINT PRIMARY KEY AUTO_INCREMENT, order_id BIGINT, product_id BIGINT, "
                            + "FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE, "
                            + "FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE)");
            statement.execute("INSERT INTO products (id, main_image) VALUES (1, 'legacy')");
            statement.execute("INSERT INTO product_images (url, product_id) VALUES ('legacy', 1)");

            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            try (ResultSet columns = statement.executeQuery("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'product_images'")) {
                java.util.Set<String> names = new java.util.HashSet<>();
                while (columns.next()) names.add(columns.getString(1));
                assertThat(names)
                        .contains("media_id", "display_order", "product_id")
                        .doesNotContain("url");
            }
            try (ResultSet productId = statement.executeQuery(
                    "SELECT is_nullable FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'product_images' AND column_name = 'product_id'")) {
                assertThat(productId.next()).isTrue();
                assertThat(productId.getString(1)).isEqualTo("NO");
            }
            try (ResultSet fk =
                    statement.executeQuery("SELECT delete_rule FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema = DATABASE() AND table_name = 'order_items' "
                            + "AND referenced_table_name = 'products'")) {
                assertThat(fk.next()).isTrue();
                assertThat(fk.getString(1)).isEqualTo("RESTRICT");
            }
        }
    }
}
