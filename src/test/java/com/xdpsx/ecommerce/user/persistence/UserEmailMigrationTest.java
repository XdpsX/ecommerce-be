package com.xdpsx.ecommerce.user.persistence;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

/** Verifies canonical email backfill, collision safety, and the named global unique constraint. */
@Testcontainers
class UserEmailMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-17.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("user_email_migration");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users ("
                    + "id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL, email VARCHAR(64) NOT NULL, "
                    + "password VARCHAR(255), avatar VARCHAR(255), role VARCHAR(32) NOT NULL, "
                    + "auth_provider VARCHAR(32) NOT NULL)");
        }
    }

    @Test
    void migration_ShouldCanonicalizeRowsPreserveOwnershipAndEnforceUniqueEmail() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (id, name, email, role, auth_provider) VALUES "
                    + "(1, 'Alice', CONCAT(CHAR(9), 'Alice@Example.COM', CHAR(9)), 'USER', 'LOCAL'), "
                    + "(2, 'Google', 'google@example.com', 'USER', 'GOOGLE')");

            runChangeset(connection);

            try (ResultSet result =
                    statement.executeQuery("SELECT id, email, role, auth_provider FROM users ORDER BY id")) {
                result.next();
                assertThat(result.getString("email")).isEqualTo("alice@example.com");
                assertThat(result.getString("role")).isEqualTo("USER");
                assertThat(result.getString("auth_provider")).isEqualTo("LOCAL");
                result.next();
                assertThat(result.getString("email")).isEqualTo("google@example.com");
                assertThat(result.getString("role")).isEqualTo("USER");
                assertThat(result.getString("auth_provider")).isEqualTo("GOOGLE");
            }

            assertThatThrownBy(() -> statement.execute("INSERT INTO users "
                            + "(id, name, email, role, auth_provider) VALUES "
                            + "(3, 'Duplicate', 'ALICE@EXAMPLE.COM', 'USER', 'LOCAL')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_users_email");
        }
    }

    @Test
    void migration_ShouldRejectCanonicalCollisionBeforeChangingRows() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (id, name, email, role, auth_provider) VALUES "
                    + "(1, 'Alice', CONCAT(CHAR(9), 'alice@example.com', CHAR(9)), 'USER', 'LOCAL'), "
                    + "(2, 'Other', 'alice@example.com', 'ADMIN', 'GOOGLE')");

            assertThatThrownBy(() -> runChangeset(connection))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_user_email_identity_guard");

            try (ResultSet result = statement.executeQuery("SELECT id, email FROM users ORDER BY id")) {
                result.next();
                assertThat(result.getString("email")).isEqualTo("\talice@example.com\t");
                result.next();
                assertThat(result.getString("email")).isEqualTo("alice@example.com");
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
                .collect(java.util.stream.Collectors.joining("\n"));
        try (Statement statement = connection.createStatement()) {
            for (String command : withoutComments.split(";")) {
                if (!command.trim().isEmpty()) {
                    statement.execute(command.trim());
                }
            }
        }
    }
}
