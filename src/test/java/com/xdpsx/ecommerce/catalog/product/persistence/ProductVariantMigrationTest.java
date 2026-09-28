package com.xdpsx.ecommerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Verifies the MySQL constraints that make SKU identity and option ownership durable. */
@Testcontainers
class ProductVariantMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-11.sql");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("product_variant_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migration_ShouldCreateSkuConstraintsAndRejectMismatchedOptionValue() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE products (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE variant_options (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE variant_option_values (id BIGINT PRIMARY KEY, option_id BIGINT NOT NULL, "
                    + "CONSTRAINT fk_test_value_option FOREIGN KEY (option_id) REFERENCES variant_options(id))");
            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            assertThat(indexNames(connection, "product_variants"))
                    .contains("uk_product_variant_sku", "uk_product_variant_barcode", "uk_product_variant_combination");
            assertThat(indexNames(connection, "product_variant_selections"))
                    .contains("PRIMARY", "ix_variant_selection_option_value");

            statement.execute("INSERT INTO variant_options (id) VALUES (1), (2)");
            statement.execute("INSERT INTO variant_option_values (id, option_id) VALUES (11, 1), (21, 2)");
            statement.execute("INSERT INTO products (id) VALUES (10)");
            statement.execute("INSERT INTO product_variants (id, product_id, sku, status, combination_key) "
                    + "VALUES (100, 10, 'SKU-1', 'ACTIVE', '1=11')");
            statement.execute("INSERT INTO product_variant_selections (variant_id, option_id, option_value_id) "
                    + "VALUES (100, 1, 11)");

            assertThatThrownBy(() -> statement.execute(
                            "INSERT INTO product_variant_selections (variant_id, option_id, option_value_id) "
                                    + "VALUES (100, 2, 11)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static Set<String> indexNames(Connection connection, String tableName) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexes.getString("INDEX_NAME") != null) names.add(indexes.getString("INDEX_NAME"));
            }
        }
        return names;
    }
}
