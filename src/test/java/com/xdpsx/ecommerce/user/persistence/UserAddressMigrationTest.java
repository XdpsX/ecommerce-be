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
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

@Testcontainers
class UserAddressMigrationTest {
    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-19.sql");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("user_address_migration");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS user_addresses");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO users (id) VALUES (1), (2)");
        }
    }

    @Test
    void migration_ShouldCreateNamedSchemaAndCascadeOnlyTheDeletedUsersRows() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            runChangeset(connection);

            assertThat(count(
                            statement,
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE table_schema = DATABASE() AND table_name = 'user_addresses'"))
                    .isEqualTo(1);
            assertThat(count(
                            statement,
                            "SELECT COUNT(*) FROM information_schema.columns "
                                    + "WHERE table_schema = DATABASE() AND table_name = 'user_addresses'"))
                    .isEqualTo(9);
            assertThat(count(
                            statement,
                            "SELECT COUNT(*) FROM information_schema.statistics "
                                    + "WHERE table_schema = DATABASE() AND table_name = 'user_addresses' "
                                    + "AND index_name = 'ix_user_addresses_user_id'"))
                    .isEqualTo(1);
            // MySQL exposes primary keys as PRIMARY, ignoring the name supplied in the DDL.
            assertThat(count(
                            statement,
                            "SELECT COUNT(*) FROM information_schema.table_constraints "
                                    + "WHERE constraint_schema = DATABASE() AND table_name = 'user_addresses' "
                                    + "AND constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY'"))
                    .isEqualTo(1);
            assertThat(count(
                            statement,
                            "SELECT COUNT(*) FROM information_schema.table_constraints "
                                    + "WHERE constraint_schema = DATABASE() AND table_name = 'user_addresses' "
                                    + "AND constraint_name = 'fk_user_addresses_user'"))
                    .isEqualTo(1);

            statement.execute("INSERT INTO user_addresses "
                    + "(user_id, recipient_name, phone_number, address_line, ward_commune, district, province_city) "
                    + "VALUES (1, 'Alice', '+84901234567', 'Street 1', 'Ward 1', 'District 1', 'City'), "
                    + "(1, 'Alice', '+84901234567', 'Street 2', 'Ward 2', 'District 2', 'City'), "
                    + "(2, 'Bob', '+84901234567', 'Street', 'Ward', 'District', 'City')");
            assertThatThrownBy(() -> statement.execute("INSERT INTO user_addresses "
                            + "(user_id, recipient_name, phone_number, address_line, ward_commune, district, province_city) "
                            + "VALUES (999, 'Unknown', '+84901234567', 'Street', 'Ward', 'District', 'City')"))
                    .isInstanceOf(SQLException.class);

            statement.execute("DELETE FROM users WHERE id = 1");
            assertThat(count(statement, "SELECT COUNT(*) FROM user_addresses WHERE user_id = 1"))
                    .isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM user_addresses WHERE user_id = 2"))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    statement,
                                    "SELECT COUNT(*) FROM information_schema.columns "
                                            + "WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'address_id'"))
                    .isZero();
        }
    }

    private static int count(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
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
