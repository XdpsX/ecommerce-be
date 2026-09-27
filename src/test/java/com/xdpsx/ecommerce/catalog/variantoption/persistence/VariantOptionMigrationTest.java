package com.xdpsx.ecommerce.catalog.variantoption.persistence;

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
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Verifies the option dictionary migration itself, rather than Hibernate's generated schema. */
@Testcontainers
class VariantOptionMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-10.sql");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("variant_option_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migration_ShouldCreateDictionaryConstraintsForeignKeyAndIndexes() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            String withoutComments = Arrays.stream(sql.split("\\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }

            assertThat(tableExists(connection, "variant_options")).isTrue();
            assertThat(tableExists(connection, "variant_option_values")).isTrue();
            assertThat(indexExists(connection, "variant_options", "uk_variant_option_code"))
                    .isTrue();
            assertThat(indexExists(connection, "variant_option_values", "uk_variant_option_value_code"))
                    .isTrue();
            assertThat(indexExists(connection, "variant_option_values", "uk_variant_option_value_order"))
                    .isTrue();
            assertThat(indexExists(connection, "variant_option_values", "ix_variant_option_value_status_order"))
                    .isTrue();

            try (ResultSet foreignKeys =
                    statement.executeQuery("SELECT delete_rule FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema = DATABASE() AND table_name = 'variant_option_values' "
                            + "AND constraint_name = 'fk_variant_option_value_option'")) {
                assertThat(foreignKeys.next()).isTrue();
                assertThat(foreignKeys.getString(1)).isEqualTo("RESTRICT");
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private static boolean indexExists(Connection connection, String tableName, String indexName) throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            Set<String> names = new HashSet<>();
            while (indexes.next()) {
                if (indexes.getString("INDEX_NAME") != null) names.add(indexes.getString("INDEX_NAME"));
            }
            return names.contains(indexName);
        }
    }
}
