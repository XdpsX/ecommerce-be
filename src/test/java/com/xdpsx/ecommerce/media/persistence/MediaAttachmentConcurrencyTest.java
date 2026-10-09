package com.xdpsx.ecommerce.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

@SpringJUnitConfig(MediaAttachmentConcurrencyTest.PersistenceConfig.class)
class MediaAttachmentConcurrencyTest {

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = MediaRepository.class)
    static class PersistenceConfig {
        @Bean
        javax.sql.DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:media_attachment;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(javax.sql.DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            factory.getJpaPropertyMap()
                    .put(
                            "hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
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
    }

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        mediaRepository.deleteAll();
        mediaRepository.save(Media.builder()
                .id("shared-upload")
                .externalId("external-shared-upload")
                .url("https://example.test/shared-upload.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build());
        mediaRepository.save(Media.builder()
                .id("second-upload")
                .externalId("external-second-upload")
                .url("https://example.test/second-upload.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build());
        mediaRepository.save(Media.builder()
                .id("processing-upload")
                .externalId("external-processing-upload")
                .url("https://example.test/processing-upload.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .processingStatus(MediaProcessingStatus.PROCESSING)
                .processingReference("processing-batch")
                .build());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        mediaRepository.deleteAll();
    }

    @Test
    void attachableRead_ShouldSerializeCompetingAttachmentsAndAllowOnlyOneActivation() throws Exception {
        CountDownLatch firstHasActivated = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        Future<Void> firstAttachment = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                Media media = mediaRepository
                        .findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE)
                        .orElseThrow();
                media.activate();
                entityManager.flush();
                firstHasActivated.countDown();
                await(allowFirstCommit);
            });
            return null;
        });

        assertThat(firstHasActivated.await(5, TimeUnit.SECONDS)).isTrue();
        Future<Optional<Media>> competingAttachment = executor.submit(() -> transactionTemplate.execute(
                status -> mediaRepository.findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE)));

        // H2 may immediately exclude a row whose uncommitted update no longer matches the query, while MySQL
        // waits for the lock owner to commit. Either behavior is safe; seeing the upload as attachable is not.
        try {
            assertThat(competingAttachment.get(250, TimeUnit.MILLISECONDS)).isEmpty();
        } catch (java.util.concurrent.TimeoutException expectedLockWait) {
            assertThat(competingAttachment).isNotDone();
        }

        allowFirstCommit.countDown();
        firstAttachment.get(5, TimeUnit.SECONDS);
        assertThat(competingAttachment.get(5, TimeUnit.SECONDS)).isEmpty();
        assertThat(mediaRepository.findById("shared-upload").orElseThrow().getAttachmentStatus())
                .isEqualTo(MediaAttachmentStatus.ACTIVE);
    }

    @Test
    void temporaryClaim_ShouldYieldToConcurrentAttachment() throws Exception {
        CountDownLatch firstHasActivated = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        Future<Void> attachment = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                Media media = mediaRepository
                        .findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE)
                        .orElseThrow();
                media.activate();
                entityManager.flush();
                firstHasActivated.countDown();
                await(allowFirstCommit);
            });
            return null;
        });

        assertThat(firstHasActivated.await(5, TimeUnit.SECONDS)).isTrue();
        Future<Integer> claim = executor.submit(() ->
                transactionTemplate.execute(status -> mediaRepository.claimTemporaryForDeletion("shared-upload")));

        try {
            assertThat(claim.get(250, TimeUnit.MILLISECONDS)).isEqualTo(0);
        } catch (java.util.concurrent.TimeoutException expectedLockWait) {
            assertThat(claim).isNotDone();
        }

        allowFirstCommit.countDown();
        attachment.get(5, TimeUnit.SECONDS);
        assertThat(claim.get(5, TimeUnit.SECONDS)).isEqualTo(0);
        assertThat(mediaRepository.findById("shared-upload").orElseThrow().getAttachmentStatus())
                .isEqualTo(MediaAttachmentStatus.ACTIVE);
    }

    @Test
    void bulkMediaLock_ShouldSerializeCompetingAttachmentReads() throws Exception {
        CountDownLatch bulkLockAcquired = new CountDownLatch(1);
        CountDownLatch allowBulkCommit = new CountDownLatch(1);

        Future<Void> productImageUpdate = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                assertThat(mediaRepository.findAllByIdInForUpdate(java.util.List.of("shared-upload", "second-upload")))
                        .hasSize(2);
                bulkLockAcquired.countDown();
                await(allowBulkCommit);
            });
            return null;
        });

        assertThat(bulkLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
        Future<Optional<Media>> competingAttachment = executor.submit(() -> transactionTemplate.execute(
                status -> mediaRepository.findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE)));

        try {
            assertThat(competingAttachment.get(250, TimeUnit.MILLISECONDS)).isPresent();
        } catch (java.util.concurrent.TimeoutException expectedLockWait) {
            assertThat(competingAttachment).isNotDone();
        }

        allowBulkCommit.countDown();
        productImageUpdate.get(5, TimeUnit.SECONDS);
        assertThat(competingAttachment.get(5, TimeUnit.SECONDS)).isPresent();
    }

    @Test
    void processingLock_ShouldKeepTheFirstTerminalOutcome() throws Exception {
        CountDownLatch firstHasCompleted = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        Future<Void> firstOutcome = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                Media media = mediaRepository
                        .findByProcessingReferenceForUpdate("processing-batch")
                        .orElseThrow();
                media.markProcessingReady();
                entityManager.flush();
                firstHasCompleted.countDown();
                await(allowFirstCommit);
            });
            return null;
        });

        assertThat(firstHasCompleted.await(5, TimeUnit.SECONDS)).isTrue();
        Future<Void> competingOutcome = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                Media media = mediaRepository
                        .findByProcessingReferenceForUpdate("processing-batch")
                        .orElseThrow();
                media.markProcessingFailed("later failure");
            });
            return null;
        });

        try {
            competingOutcome.get(250, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException expectedLockWait) {
            assertThat(competingOutcome).isNotDone();
        }

        allowFirstCommit.countDown();
        firstOutcome.get(5, TimeUnit.SECONDS);
        competingOutcome.get(5, TimeUnit.SECONDS);
        assertThat(mediaRepository.findById("processing-upload").orElseThrow().getProcessingStatus())
                .isEqualTo(MediaProcessingStatus.READY);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating the attachment transactions");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating the attachment transactions", exception);
        }
    }
}
