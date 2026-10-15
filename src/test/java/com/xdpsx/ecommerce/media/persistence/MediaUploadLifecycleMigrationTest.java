package com.xdpsx.ecommerce.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(MediaUploadLifecycleMigrationTest.PersistenceConfig.class)
class MediaUploadLifecycleMigrationTest {
    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("media_upload_lifecycle");

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = MediaRepository.class)
    static class PersistenceConfig {
        @Bean
        javax.sql.DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(MYSQL.getDriverClassName());
            dataSource.setUrl(MYSQL.getJdbcUrl());
            dataSource.setUsername(MYSQL.getUsername());
            dataSource.setPassword(MYSQL.getPassword());
            return dataSource;
        }

        @Bean
        liquibase.integration.spring.SpringLiquibase liquibase(javax.sql.DataSource dataSource) {
            liquibase.integration.spring.SpringLiquibase liquibase = new liquibase.integration.spring.SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
            return liquibase;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
                javax.sql.DataSource dataSource, liquibase.integration.spring.SpringLiquibase liquibase) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "none");
            factory.getJpaPropertyMap()
                    .put(
                            "hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        JdbcTemplate jdbcTemplate(javax.sql.DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void activeBaseline_ShouldApplyUploadLifecycleSchemaAndPersistIntent() {
        assertThat(jdbcTemplate.queryForList("SELECT id FROM DATABASECHANGELOG ORDER BY orderexecuted", String.class))
                .contains("media-upload-lifecycle");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'media' "
                                + "AND column_name = 'upload_status'",
                        String.class))
                .isEqualTo("NO");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'media' "
                                + "AND column_name = 'url'",
                        String.class))
                .isEqualTo("YES");

        String id = transactionTemplate.execute(status -> {
            Media media = Media.createPendingUpload(
                    "pending-migration-test",
                    "product/pending-migration-test",
                    42L,
                    MediaPurpose.PRODUCT_IMAGE,
                    "image/png",
                    2048,
                    java.time.LocalDateTime.now().plusHours(1));
            entityManager.persist(media);
            entityManager.flush();
            entityManager.clear();
            return mediaRepository.findById(media.getId()).orElseThrow().getId();
        });

        Media loaded = mediaRepository.findById(id).orElseThrow();
        assertThat(loaded.getUploadStatus()).isEqualTo(MediaUploadStatus.PENDING_UPLOAD);
        assertThat(loaded.getProcessingStatus()).isEqualTo(MediaProcessingStatus.PENDING);
        assertThat(loaded.getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.TEMPORARY);
        assertThat(loaded.getUrl()).isNull();
        assertThat(loaded.getContentType()).isNull();
    }

    @Test
    void uploadLifecycleChecks_ShouldRejectInvalidDatabaseRows() {
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO media (id, external_id, purpose, upload_status, attachment_status, processing_status, "
                                + "intent_expires_at, expected_content_type, expected_size_bytes) "
                                + "VALUES ('invalid-pending', 'invalid/pending', 'PRODUCT_IMAGE', 'PENDING_UPLOAD', "
                                + "'ACTIVE', 'PENDING', CURRENT_TIMESTAMP(6), 'image/png', 1024)"));

        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO media (id, external_id, purpose, upload_status, attachment_status, processing_status) "
                                + "VALUES ('invalid-uploaded', 'invalid/uploaded', 'PRODUCT_IMAGE', 'UPLOADED', "
                                + "'TEMPORARY', 'PROCESSING')"));
    }
}
