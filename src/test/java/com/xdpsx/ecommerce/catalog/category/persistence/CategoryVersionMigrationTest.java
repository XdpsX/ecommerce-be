package com.xdpsx.ecommerce.catalog.category.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

/** Verifies the Category version changeset against an existing MySQL row. */
@Testcontainers
@SpringJUnitConfig(CategoryVersionMigrationTest.PersistenceConfig.class)
class CategoryVersionMigrationTest {
    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("category_version_migration");

    @org.springframework.context.annotation.Configuration
    static class PersistenceConfig {
        @Bean
        DataSource dataSource() throws SQLException {
            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE categories (id INT PRIMARY KEY, name VARCHAR(128) NOT NULL)");
                statement.execute("INSERT INTO categories (id, name) VALUES (1, 'Existing category')");
            }
            return dataSource;
        }

        @Bean
        liquibase.integration.spring.SpringLiquibase liquibase(DataSource dataSource) {
            liquibase.integration.spring.SpringLiquibase liquibase = new liquibase.integration.spring.SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog("classpath:db/changelog/category-version-migration-test.yaml");
            return liquibase;
        }
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void migration_ShouldBackfillExistingRowsAndKeepVersionNonNullWithZeroDefault() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject("SELECT version FROM categories WHERE id = 1", Long.class))
                .isEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT IS_NULLABLE FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'categories' "
                                + "AND column_name = 'version'",
                        String.class))
                .isEqualTo("NO");

        jdbcTemplate.update("INSERT INTO categories (id, name) VALUES (2, 'New category')");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM categories WHERE id = 2", Long.class))
                .isEqualTo(0L);
    }
}
