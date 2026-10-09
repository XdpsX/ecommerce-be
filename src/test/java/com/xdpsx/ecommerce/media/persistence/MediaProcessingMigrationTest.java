package com.xdpsx.ecommerce.media.persistence;

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

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

/** Verifies the async media columns against the legacy media schema on MySQL. */
@Testcontainers
class MediaProcessingMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-26.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("media_processing_migration");

    private static List<String> statements(String sql) {
        String withoutComments = Arrays.stream(sql.split("\\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(java.util.stream.Collectors.joining("\\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    @Test
    void migration_ShouldBackfillReadyAndPreserveAttachmentStatuses() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE media ("
                    + "id VARCHAR(36) NOT NULL PRIMARY KEY, external_id VARCHAR(255) NOT NULL UNIQUE, "
                    + "url VARCHAR(255) NOT NULL, caption VARCHAR(100), content_type VARCHAR(30) NOT NULL, "
                    + "purpose VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL, "
                    + "updated_at TIMESTAMP NULL)");
            statement.execute("INSERT INTO media "
                    + "(id, external_id, url, content_type, purpose, status, created_at) VALUES "
                    + "('temporary', 'asset-1', 'https://example.test/1', 'image/jpeg', 'PRODUCT_IMAGE', "
                    + "'TEMPORARY', CURRENT_TIMESTAMP), "
                    + "('active', 'asset-2', 'https://example.test/2', 'image/jpeg', 'PRODUCT_IMAGE', "
                    + "'ACTIVE', CURRENT_TIMESTAMP), "
                    + "('pending', 'asset-3', 'https://example.test/3', 'image/jpeg', 'PRODUCT_IMAGE', "
                    + "'PENDING_DELETE', CURRENT_TIMESTAMP)");

            for (String sql : statements(Files.readString(CHANGESET, StandardCharsets.UTF_8))) {
                statement.execute(sql);
            }

            try (ResultSet rows =
                    statement.executeQuery("SELECT id, attachment_status, processing_status FROM media ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("id")).isEqualTo("active");
                assertThat(rows.getString("attachment_status")).isEqualTo("ACTIVE");
                assertThat(rows.getString("processing_status")).isEqualTo("READY");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("id")).isEqualTo("pending");
                assertThat(rows.getString("attachment_status")).isEqualTo("PENDING_DELETE");
                assertThat(rows.getString("processing_status")).isEqualTo("READY");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("id")).isEqualTo("temporary");
                assertThat(rows.getString("attachment_status")).isEqualTo("TEMPORARY");
                assertThat(rows.getString("processing_status")).isEqualTo("READY");
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
