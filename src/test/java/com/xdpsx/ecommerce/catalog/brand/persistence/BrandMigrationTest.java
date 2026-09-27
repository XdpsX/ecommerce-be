package com.xdpsx.ecommerce.catalog.brand.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Runs the Brand lifecycle migration against MySQL rows from the legacy schema. */
@Testcontainers
class BrandMigrationTest {

    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-8.sql");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("brand_migration")
            .withUsername("test")
            .withPassword("test");

    private static List<String> statements(String sql) {
        String withoutComments = Arrays.stream(sql.split("\\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(java.util.stream.Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    @Test
    void migration_ShouldBackfillLegacyPublicAndPrivateBrandsAndPreserveImageIds() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE brands (id INT PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                    + "public_flg BOOLEAN NOT NULL, image_id VARCHAR(36) NULL)");
            statement.execute("INSERT INTO brands (id, name, public_flg, image_id) VALUES "
                    + "(1, 'Public', TRUE, 'public-logo'), (2, 'Private', FALSE, 'private-logo')");

            String changeset = Files.readString(CHANGESET, StandardCharsets.UTF_8);
            for (String sql : statements(changeset)) {
                statement.execute(sql);
            }

            try (ResultSet rows =
                    statement.executeQuery("SELECT id, status, version, image_id FROM brands ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("id")).isEqualTo(1);
                assertThat(rows.getString("status")).isEqualTo("ACTIVE");
                assertThat(rows.getLong("version")).isZero();
                assertThat(rows.getString("image_id")).isEqualTo("public-logo");

                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("id")).isEqualTo(2);
                assertThat(rows.getString("status")).isEqualTo("INACTIVE");
                assertThat(rows.getLong("version")).isZero();
                assertThat(rows.getString("image_id")).isEqualTo("private-logo");
                assertThat(rows.next()).isFalse();
            }

            try (ResultSet metadata = statement.executeQuery(
                    "SELECT IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'brands' AND column_name = 'version'")) {
                assertThat(metadata.next()).isTrue();
                assertThat(metadata.getString("IS_NULLABLE")).isEqualTo("NO");
                assertThat(metadata.getString("COLUMN_DEFAULT")).isEqualTo("0");
            }
        }
    }
}
