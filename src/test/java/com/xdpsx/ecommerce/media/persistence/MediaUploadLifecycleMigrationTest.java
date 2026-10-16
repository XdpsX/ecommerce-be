package com.xdpsx.ecommerce.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
                .contains("media-upload-lifecycle", "media-direct-upload-verification", "media-reconciliation");
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
            java.time.LocalDateTime issued = java.time.LocalDateTime.now().minusMinutes(1);
            Media media = Media.createSignedPendingUpload(
                    "pending-migration-test",
                    "product/pending-migration-test",
                    42L,
                    MediaPurpose.PRODUCT_IMAGE,
                    "image/png",
                    2048,
                    java.time.LocalDateTime.now().plusHours(1),
                    issued,
                    issued.plusHours(1));
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
    void directUploadColumns_ShouldPersistVerifiedMetadataAndKeepLegacyColumnsNullable() {
        transactionTemplate.executeWithoutResult(status -> {
            Media legacy = Media.createUploaded(
                    "legacy-direct-migration",
                    "legacy/public-id",
                    "https://cdn.example.test/legacy.png",
                    null,
                    "image/png",
                    MediaPurpose.PRODUCT_IMAGE,
                    null,
                    MediaAttachmentStatus.TEMPORARY,
                    "legacy-batch");
            entityManager.persist(legacy);

            java.time.LocalDateTime issued = java.time.LocalDateTime.now().minusMinutes(1);
            Media direct = Media.createSignedPendingUpload(
                    "direct-migration",
                    "product/direct-migration",
                    42L,
                    MediaPurpose.PRODUCT_IMAGE,
                    "image/png",
                    4096,
                    java.time.LocalDateTime.now().plusHours(1),
                    issued,
                    issued.plusHours(1));
            direct.completeUpload(
                    "asset-direct-1",
                    "https://cdn.example.test/direct.png",
                    "image/png",
                    4096,
                    800,
                    600,
                    "png",
                    issued.plusSeconds(5));
            entityManager.persist(direct);
            entityManager.flush();
            entityManager.clear();

            Media reloadedLegacy = mediaRepository.findById(legacy.getId()).orElseThrow();
            assertThat(reloadedLegacy.getAssetId()).isNull();
            assertThat(reloadedLegacy.getUploadAuthorizationIssuedAt()).isNull();
            assertThat(reloadedLegacy.getBytes()).isNull();

            Media reloadedDirect = mediaRepository.findById(direct.getId()).orElseThrow();
            assertThat(reloadedDirect.getAssetId()).isEqualTo("asset-direct-1");
            assertThat(reloadedDirect.getBytes()).isEqualTo(4096L);
            assertThat(reloadedDirect.getWidth()).isEqualTo(800);
            assertThat(reloadedDirect.getHeight()).isEqualTo(600);
            assertThat(reloadedDirect.getFormat()).isEqualTo("png");
        });
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

    @Test
    void directUploadChecks_ShouldRejectIncompleteSignedUploadedMetadata() {
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO media (id, external_id, purpose, upload_status, attachment_status, processing_status, "
                                + "url, content_type, upload_authorization_issued_at, upload_authorization_expires_at) "
                                + "VALUES ('incomplete-direct', 'product/incomplete-direct', 'PRODUCT_IMAGE', 'UPLOADED', "
                                + "'TEMPORARY', 'PROCESSING', 'https://cdn.example.test/a.png', 'image/png', "
                                + "CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR))"));
    }

    @Test
    void directUploadAssetIdentity_ShouldBeUnique() {
        String columns = "(id, external_id, purpose, upload_status, attachment_status, processing_status, url, "
                + "content_type, created_at, asset_id, upload_authorization_issued_at, "
                + "upload_authorization_expires_at, uploaded_at, bytes, width, height, format) ";
        String values = "VALUES (?, ?, 'PRODUCT_IMAGE', 'UPLOADED', 'TEMPORARY', 'PROCESSING', "
                + "'https://cdn.example.test/asset.png', 'image/png', CURRENT_TIMESTAMP(6), ?, "
                + "CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR), "
                + "CURRENT_TIMESTAMP(6), 2048, 800, 600, 'png')";
        jdbcTemplate.update(
                "INSERT INTO media " + columns + values,
                "asset-unique-one",
                "product/asset-unique-one",
                "provider-asset-id");

        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO media " + columns + values,
                        "asset-unique-two",
                        "product/asset-unique-two",
                        "provider-asset-id"));
    }

    @Test
    void reconciliationBaseline_ShouldPersistDefaultsAndExecuteBoundedCandidateQueries() {
        LocalDateTime now =
                LocalDateTime.ofInstant(java.time.Instant.now(), ZoneOffset.UTC).withNano(0);
        insertPending("recon-pending-initial", now.minusHours(2), null);
        insertPending("recon-pending-retry", now.minusHours(3), now.minusHours(1));
        insertPending("recon-pending-future", now.minusHours(4), now.plusHours(1));

        insertUploadedProcessing("recon-process-initial", now.minusHours(2), null);
        insertUploadedProcessing("recon-process-retry", now.minusHours(3), now.minusHours(1));
        insertUploadedProcessing("recon-process-future", now.minusHours(4), now.plusHours(1));
        insertUploadedProcessing("recon-process-horizon", now.minusDays(8), null);

        var pending = mediaRepository.findPendingUploadReconciliationCandidates(now.minusMinutes(10), now, 600, 100);
        var processing = mediaRepository.findProcessingReconciliationCandidates(
                now.minusMinutes(10), now.minusDays(7), now, 600, 100);

        assertThat(pending)
                .extracting(Media::getId)
                .contains("recon-pending-initial", "recon-pending-retry")
                .doesNotContain("recon-pending-future");
        assertThat(processing)
                .extracting(Media::getId)
                .contains("recon-process-initial", "recon-process-retry")
                .doesNotContain("recon-process-future", "recon-process-horizon");
        java.util.List<String> pendingIds = pending.stream().map(Media::getId).toList();
        java.util.List<String> processingIds =
                processing.stream().map(Media::getId).toList();
        assertThat(pendingIds.indexOf("recon-pending-initial")).isLessThan(pendingIds.indexOf("recon-pending-retry"));
        assertThat(processingIds.indexOf("recon-process-initial"))
                .isLessThan(processingIds.indexOf("recon-process-retry"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT processing_reconciliation_attempts FROM media WHERE id = 'recon-process-initial'",
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT reconciliation_next_attempt_at FROM media WHERE id = 'recon-process-initial'",
                        java.sql.Timestamp.class))
                .isNull();
    }

    @Test
    void reconciliationAttemptConstraint_ShouldRejectNegativeCounters() {
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO media (id, external_id, purpose, upload_status, attachment_status, "
                                + "processing_status, processing_reconciliation_attempts) "
                                + "VALUES ('negative-attempt', 'negative-attempt', 'PRODUCT_IMAGE', 'UPLOADED', "
                                + "'TEMPORARY', 'PROCESSING', -1)"));
    }

    private void insertPending(String id, LocalDateTime expiresAt, LocalDateTime nextAttemptAt) {
        jdbcTemplate.update(
                "INSERT INTO media (id, external_id, purpose, upload_status, attachment_status, processing_status, "
                        + "created_at, intent_expires_at, expected_content_type, expected_size_bytes, "
                        + "upload_authorization_issued_at, upload_authorization_expires_at, reconciliation_next_attempt_at) "
                        + "VALUES (?, ?, 'PRODUCT_IMAGE', 'PENDING_UPLOAD', 'TEMPORARY', 'PENDING', CURRENT_TIMESTAMP(6), "
                        + "?, 'image/png', 1024, ?, ?, ?)",
                id,
                id,
                expiresAt.plusHours(1),
                expiresAt.minusHours(1),
                expiresAt,
                nextAttemptAt);
    }

    private void insertUploadedProcessing(String id, LocalDateTime uploadedAt, LocalDateTime nextAttemptAt) {
        jdbcTemplate.update(
                "INSERT INTO media (id, external_id, purpose, upload_status, attachment_status, processing_status, "
                        + "created_at, url, content_type, upload_authorization_issued_at, upload_authorization_expires_at, "
                        + "uploaded_at, asset_id, bytes, width, height, format, reconciliation_next_attempt_at) "
                        + "VALUES (?, ?, 'PRODUCT_IMAGE', 'UPLOADED', 'TEMPORARY', 'PROCESSING', ?, "
                        + "'https://cdn.example.test/a.png', 'image/png', ?, ?, ?, ?, 1024, 800, 600, 'png', ?)",
                id,
                id,
                uploadedAt,
                uploadedAt.minusHours(1),
                uploadedAt.plusHours(1),
                uploadedAt,
                "asset-" + id,
                nextAttemptAt);
    }
}
