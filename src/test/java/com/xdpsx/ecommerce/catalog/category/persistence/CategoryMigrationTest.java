package com.xdpsx.ecommerce.catalog.category.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Executes the Category backfill in {@code changeset-6.sql} on a real MySQL server.
 *
 * <p>The backfill is MySQL specific (window functions, {@code REGEXP_REPLACE}, a generated transliteration table,
 * and an {@code UPDATE} that reads a derived table of the table it writes). {@code CategorySlugMigrationConsistencyTest}
 * only models the collision rule in Java, so it cannot prove the SQL parses or behaves as intended. This test runs
 * the real file, statement by statement, the same way Liquibase executes a formatted-SQL changeset.
 *
 * <p>Each class run owns an isolated MySQL 8.4 container. It never connects to the development database and the
 * container is destroyed after the class, so resetting the fixture cannot delete user data.
 *
 * <p>The fixture at {@code src/test/resources/db/category-cr1-legacy-fixture.sql} documents the expected result
 * and can also be applied by hand.
 */
@Testcontainers
class CategoryMigrationTest {

    private static final Path CHANGESET = Path.of("src/main/resources/db/changelog/changesets/changeset-6.sql");
    private static final Path FIXTURE = Path.of("src/test/resources/db/category-cr1-legacy-fixture.sql");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("category_migration")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    /**
     * Ids 4 and 12 collide across the two collision sources: id 4 is punctuation-only and falls back to
     * {@code category-4}, while id 12 "Category 4" normalizes to the same value.
     */
    private static final Map<Integer, String> EXPECTED_SLUGS = expectedSlugs();

    private static final Map<Integer, String> EXPECTED_STATUS = Map.ofEntries(
            Map.entry(1, "ACTIVE"),
            Map.entry(2, "ACTIVE"),
            Map.entry(3, "ACTIVE"),
            Map.entry(4, "INACTIVE"),
            Map.entry(5, "ACTIVE"),
            Map.entry(6, "ACTIVE"),
            Map.entry(7, "ACTIVE"),
            Map.entry(8, "ACTIVE"),
            Map.entry(9, "ACTIVE"),
            Map.entry(10, "INACTIVE"),
            Map.entry(11, "INACTIVE"),
            Map.entry(12, "ACTIVE"));

    private static Map<Integer, String> expectedSlugs() {
        Map<Integer, String> slugs = new LinkedHashMap<>();
        slugs.put(1, "foo");
        slugs.put(2, "foo-2");
        slugs.put(3, "foo-2-3");
        slugs.put(4, "category-4");
        slugs.put(5, "dien-thoai");
        slugs.put(6, "dien-thoai-6");
        slugs.put(7, "giay-dep");
        slugs.put(8, "may-anh");
        slugs.put(9, "ong-kinh");
        slugs.put(10, "phu-kien-cho-dien-thoai-va-may-tinh-bang");
        slugs.put(11, "category-11");
        slugs.put(12, "category-4-12");
        return Map.copyOf(slugs);
    }

    private static String databaseUrl() {
        // allowMultiQueries lets one test submit the whole changeset as a single script, which proves the file is
        // valid SQL end to end and not only statement by statement.
        String separator = MYSQL.getJdbcUrl().contains("?") ? "&" : "?";
        return MYSQL.getJdbcUrl() + separator + "allowMultiQueries=true";
    }

    @BeforeEach
    void resetFixture() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS categories");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(databaseUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }

    /**
     * Splits formatted SQL the way Liquibase does: comment lines are removed, then statements are separated by
     * {@code ;}. The changeset deliberately contains no procedures or multi-statement blocks.
     */
    private static List<String> statements(String sql) {
        String withoutComments = Arrays.stream(sql.split("\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private static void loadFixture(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements(read(FIXTURE))) {
                statement.execute(sql);
            }
        }
    }

    private static void runChangeset(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements(read(CHANGESET))) {
                statement.execute(sql);
            }
        }
    }

    private record CategoryRow(int id, String slug, String status, int displayOrder, Integer parentId) {}

    private static List<CategoryRow> readCategories(Connection connection) throws SQLException {
        List<CategoryRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT id, slug, status, display_order, parent_id FROM categories ORDER BY id")) {
            while (rs.next()) {
                rows.add(new CategoryRow(
                        rs.getInt("id"),
                        rs.getString("slug"),
                        rs.getString("status"),
                        rs.getInt("display_order"),
                        rs.getObject("parent_id") == null ? null : rs.getInt("parent_id")));
            }
        }
        return rows;
    }

    @Test
    void migration_ShouldResolveEveryCollisionWithoutLosingRows() throws Exception {
        try (Connection connection = connection()) {
            loadFixture(connection);
            runChangeset(connection);

            List<CategoryRow> rows = readCategories(connection);

            assertThat(rows).hasSize(EXPECTED_SLUGS.size());
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.slug()).as("id %d slug", row.id()).isEqualTo(EXPECTED_SLUGS.get(row.id()));
                assertThat(row.status()).as("id %d status", row.id()).isEqualTo(EXPECTED_STATUS.get(row.id()));
                assertThat(row.slug()).isNotBlank();
            });
            assertThat(rows.stream().map(CategoryRow::slug).distinct().count()).isEqualTo(rows.size());
        }
    }

    @Test
    void migration_ShouldMapPublicFlagAndBackfillSiblingOrder() throws Exception {
        try (Connection connection = connection()) {
            loadFixture(connection);
            runChangeset(connection);

            Map<Integer, Integer> orderById = new LinkedHashMap<>();
            Map<Integer, Integer> parentById = new LinkedHashMap<>();
            readCategories(connection).forEach(row -> {
                orderById.put(row.id(), row.displayOrder());
                parentById.put(row.id(), row.parentId());
            });

            // Root group order follows the original id order, starting at 0.
            assertThat(orderById.get(1)).isZero();
            assertThat(orderById.get(2)).isEqualTo(1);
            assertThat(orderById.get(3)).isEqualTo(2);
            assertThat(orderById.get(4)).isEqualTo(3);
            assertThat(orderById.get(5)).isEqualTo(4);
            assertThat(orderById.get(11)).isEqualTo(7);
            assertThat(orderById.get(12)).isEqualTo(8);

            // Child groups are ordered independently.
            assertThat(orderById.get(8)).isZero();
            assertThat(orderById.get(9)).isEqualTo(1);
            assertThat(orderById.get(10)).isZero();

            assertThat(parentById.get(8)).isEqualTo(1);
            assertThat(parentById.get(9)).isEqualTo(1);
            assertThat(parentById.get(10)).isEqualTo(2);
            assertThat(parentById.get(1)).isNull();
        }
    }

    @Test
    void migration_ShouldSucceedWhenExecutedAsOneScriptLikeLiquibaseDoes() throws Exception {
        try (Connection connection = connection()) {
            loadFixture(connection);

            // Executing the whole script through the multi-statement path also proves the statement splitting
            // used above is equivalent to what the driver accepts.
            try (Statement statement = connection.createStatement()) {
                statement.execute(read(CHANGESET));
            }

            List<CategoryRow> rows = readCategories(connection);
            assertThat(rows).hasSize(EXPECTED_SLUGS.size());
            assertThat(rows).allSatisfy(row -> assertThat(row.slug()).isEqualTo(EXPECTED_SLUGS.get(row.id())));
        }
    }

    @Test
    void schema_ShouldEnforceSlugUniquenessAndRestrictiveParentAfterMigration() throws Exception {
        try (Connection connection = connection()) {
            loadFixture(connection);
            runChangeset(connection);

            assertThatThrownBy(() -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("INSERT INTO categories (name, status, slug, display_order) "
                                    + "VALUES ('Duplicate', 'ACTIVE', 'foo', 99)");
                        }
                    })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_category_slug");

            assertThatThrownBy(() -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("DELETE FROM categories WHERE id = 1");
                        }
                    })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_parent_category");

            assertThatThrownBy(() -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute(
                                    "INSERT INTO categories (name, status, slug) VALUES ('NoOrder', 'ACTIVE', 'no-order')");
                        }
                    })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("display_order");
        }
    }
}
