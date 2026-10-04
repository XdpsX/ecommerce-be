package com.xdpsx.ecommerce.auth.persistence;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

@Testcontainers
class RefreshSessionMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-18.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("refresh_session_migration");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS refresh_sessions");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO users (id) VALUES (1), (2)");
        }
    }

    @Test
    void migration_ShouldCreateIntegrityProtectedSessionsForMultipleDevices() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            runChangeset(connection);

            statement.execute("INSERT INTO refresh_sessions "
                    + "(id, user_id, secret_hash, created_at, expires_at) VALUES "
                    + "('00000000-0000-0000-0000-000000000001', 1, REPEAT('a', 32), "
                    + "'2026-10-01 00:00:00.000000', '2026-10-31 00:00:00.000000'), "
                    + "('00000000-0000-0000-0000-000000000002', 1, REPEAT('b', 32), "
                    + "'2026-10-01 00:00:00.000000', '2026-10-31 00:00:00.000000')");

            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) AS session_count, MIN(OCTET_LENGTH(secret_hash)) AS hash_length "
                            + "FROM refresh_sessions WHERE user_id = 1")) {
                result.next();
                assertThat(result.getInt("session_count")).isEqualTo(2);
                assertThat(result.getInt("hash_length")).isEqualTo(32);
            }

            assertThatThrownBy(() -> statement.execute("INSERT INTO refresh_sessions "
                            + "(id, user_id, secret_hash, created_at, expires_at) VALUES "
                            + "('00000000-0000-0000-0000-000000000003', 999, REPEAT('c', 32), "
                            + "'2026-10-01 00:00:00.000000', '2026-10-31 00:00:00.000000')"))
                    .isInstanceOf(SQLException.class);

            statement.execute("DELETE FROM users WHERE id = 1");
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) AS session_count FROM refresh_sessions")) {
                result.next();
                assertThat(result.getInt("session_count")).isZero();
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static void runChangeset(Connection connection) throws Exception {
        String sql = Files.readString(CHANGESET, StandardCharsets.UTF_8);
        String withoutComments = Arrays.stream(sql.split("\\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
        try (Statement statement = connection.createStatement()) {
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) {
                    statement.execute(command.trim());
                }
            }
        }
    }
}
