package com.xdpsx.ecommerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

/** Verifies Variant base-price backfill and the database range invariant. */
@Testcontainers
class ProductVariantPricingMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-14.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("variant_pricing_migration");

    @Test
    void migration_ShouldBackfillVariantPricesAndEnforceBounds() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE products (id BIGINT PRIMARY KEY, price DECIMAL(15,2) NOT NULL)");
            statement.execute("CREATE TABLE product_variants (id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL, "
                    + "status VARCHAR(16) NOT NULL, "
                    + "CONSTRAINT fk_test_variant_product FOREIGN KEY (product_id) REFERENCES products(id))");
            statement.execute("INSERT INTO products (id, price) VALUES (1, 125.50), (2, 200.00), (3, 300.00)");
            statement.execute("INSERT INTO product_variants (id, product_id, status) VALUES "
                    + "(10, 1, 'ACTIVE'), (11, 1, 'ACTIVE'), (12, 1, 'INACTIVE'), (20, 2, 'INACTIVE')");

            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            try (ResultSet result = statement.executeQuery("SELECT id, base_price FROM product_variants ORDER BY id")) {
                result.next();
                assertThat(result.getBigDecimal("base_price")).isEqualByComparingTo("125.50");
                result.next();
                assertThat(result.getBigDecimal("base_price")).isEqualByComparingTo("125.50");
                result.next();
                assertThat(result.getBigDecimal("base_price")).isEqualByComparingTo("125.50");
                result.next();
                assertThat(result.getBigDecimal("base_price")).isEqualByComparingTo("200.00");
            }
            try (ResultSet result = statement.executeQuery("SELECT id, price FROM products ORDER BY id")) {
                result.next();
                assertThat(result.getBigDecimal("price")).isEqualByComparingTo("125.50");
                result.next();
                assertThat(result.getBigDecimal("price")).isEqualByComparingTo("0.00");
                result.next();
                assertThat(result.getBigDecimal("price")).isEqualByComparingTo("0.00");
            }
            assertThatThrownBy(
                            () -> statement.execute("INSERT INTO product_variants (id, product_id, status, base_price) "
                                    + "VALUES (30, 1, 'ACTIVE', -1.00)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
