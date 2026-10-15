package com.xdpsx.ecommerce.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector;
import com.xdpsx.ecommerce.media.application.MediaDirectUploadPersistence;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

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

        @Bean
        MutableClock clock() {
            return new MutableClock();
        }

        @Bean
        MediaUploadProperties mediaUploadProperties() {
            return new MediaUploadProperties();
        }

        @Bean
        MediaDirectUploadPersistence mediaDirectUploadPersistence(
                MediaRepository repository, java.time.Clock clock, MediaUploadProperties properties) {
            return new MediaDirectUploadPersistence(repository, clock, properties);
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(Instant.now());

        void setInstant(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneId.of("UTC").equals(zone)) throw new IllegalArgumentException("Only UTC is supported");
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MediaDirectUploadPersistence directUploadPersistence;

    @Autowired
    private MutableClock clock;

    @PersistenceContext
    private EntityManager entityManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        clock.setInstant(Instant.now());
        executor = Executors.newFixedThreadPool(2);
        mediaRepository.deleteAll();
        mediaRepository.save(Media.builder()
                .id("shared-upload")
                .externalId("external-shared-upload")
                .url("https://example.test/shared-upload.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadedByUserId(42L)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build());
        mediaRepository.save(Media.builder()
                .id("second-upload")
                .externalId("external-second-upload")
                .url("https://example.test/second-upload.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadedByUserId(42L)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build());
        mediaRepository.save(Media.builder()
                .id("processing-upload")
                .externalId("external-processing-upload")
                .url("https://example.test/processing-upload.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadedByUserId(42L)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .processingStatus(MediaProcessingStatus.PROCESSING)
                .processingReference("processing-batch")
                .build());
        mediaRepository.save(Media.builder()
                .id("legacy-upload")
                .externalId("external-legacy-upload")
                .url("https://example.test/legacy-upload.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
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
                        .findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE, 42L)
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
                status -> mediaRepository.findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE, 42L)));

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
    void temporaryOwnership_ShouldRejectAttachAndDeleteByAnotherUser() {
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(mediaRepository.findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE, 99L))
                    .isEmpty();
            assertThat(mediaRepository.claimTemporaryForDeletion("shared-upload", 99L))
                    .isZero();

            assertThat(mediaRepository.findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE, 42L))
                    .isPresent();
            assertThat(mediaRepository.claimTemporaryForDeletion("second-upload", 42L))
                    .isEqualTo(1);

            assertThat(mediaRepository.findAttachableById("legacy-upload", MediaPurpose.PRODUCT_IMAGE, 42L))
                    .isEmpty();
            assertThat(mediaRepository.claimTemporaryForDeletion("legacy-upload", 42L))
                    .isZero();
        });
    }

    @Test
    void pendingIntent_ShouldNotBeAttachableOrClaimedByLegacyCleanup() {
        java.time.LocalDateTime futureExpiry = java.time.LocalDateTime.now().plusDays(2);
        java.time.LocalDateTime authorizationIssuedAt =
                java.time.LocalDateTime.now().minusHours(1);
        Media pending = Media.createSignedPendingUpload(
                "pending-intent",
                "product/pending-intent",
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                1024,
                futureExpiry,
                authorizationIssuedAt,
                authorizationIssuedAt.plusHours(4));
        pending.markPendingDeletion();
        mediaRepository.save(pending);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(mediaRepository.findAttachableById("pending-intent", MediaPurpose.PRODUCT_IMAGE, 42L))
                    .isEmpty();
            LocalDateTime replayBoundary = LocalDateTime.now().minusMinutes(10);
            assertThat(mediaRepository.findExpiredTemporaryMedia(futureExpiry, replayBoundary))
                    .extracting(Media::getUploadStatus)
                    .doesNotContain(MediaUploadStatus.PENDING_UPLOAD);
            assertThat(mediaRepository.claimExpiredTemporaryForDeletion("pending-intent", futureExpiry))
                    .isZero();
            assertThat(mediaRepository.findAllByAttachmentStatusBeforeReplayBoundary(
                            MediaAttachmentStatus.PENDING_DELETE, replayBoundary))
                    .extracting(Media::getId)
                    .doesNotContain("pending-intent");
        });
    }

    @Test
    void expiredIntentClaim_ShouldNotDeleteUploadCompletedAfterInitialSnapshot() {
        LocalDateTime issuedAt = LocalDateTime.now().minusHours(2);
        Media completed = Media.createSignedPendingUpload(
                "completed-during-inspection",
                "product/completed-during-inspection",
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                1024,
                LocalDateTime.now().minusHours(1),
                issuedAt,
                issuedAt.plusHours(1));
        completed.completeUpload(
                "asset-1",
                "https://example.test/completed.png",
                "image/png",
                512,
                640,
                480,
                "png",
                issuedAt.plusMinutes(5));
        mediaRepository.save(completed);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> directUploadPersistence.claimExpired("completed-during-inspection", 42L))
                .isInstanceOf(ApplicationException.class);

        Media persisted =
                mediaRepository.findById("completed-during-inspection").orElseThrow();
        assertThat(persisted.getUploadStatus()).isEqualTo(MediaUploadStatus.UPLOADED);
        assertThat(persisted.getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.TEMPORARY);
    }

    @Test
    void temporaryClaim_ShouldYieldToConcurrentAttachment() throws Exception {
        CountDownLatch firstHasActivated = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        Future<Void> attachment = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                Media media = mediaRepository
                        .findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE, 42L)
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
                transactionTemplate.execute(status -> mediaRepository.claimTemporaryForDeletion("shared-upload", 42L)));

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
                status -> mediaRepository.findAttachableById("shared-upload", MediaPurpose.PRODUCT_IMAGE, 42L)));

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

    @Test
    void concurrentDirectFinalization_ShouldBindOneImmutableAssetAndReturnIdempotently() throws Exception {
        Media pending = pendingDirectMedia("direct-finalize");
        mediaRepository.save(pending);
        var snapshot = new MediaDirectUploadPersistence.Snapshot(
                pending.getId(),
                pending.getExternalId(),
                42L,
                pending.getPurpose(),
                pending.getUploadStatus(),
                pending.getProcessingStatus(),
                pending.getAttachmentStatus(),
                null,
                null,
                pending.getIntentExpiresAt(),
                pending.getUploadAuthorizationIssuedAt(),
                pending.getUploadAuthorizationExpiresAt());
        MediaAssetInspector.InspectedMediaAsset asset = verifiedAsset(pending.getExternalId(), "asset-immutable");

        Future<?> first =
                executor.submit(() -> directUploadPersistence.finalizeUpload(snapshot, 42L, asset, "image/png"));
        Future<?> second =
                executor.submit(() -> directUploadPersistence.finalizeUpload(snapshot, 42L, asset, "image/png"));

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        Media finalized = mediaRepository.findById(pending.getId()).orElseThrow();
        assertThat(finalized.getUploadStatus()).isEqualTo(MediaUploadStatus.UPLOADED);
        assertThat(finalized.getAssetId()).isEqualTo("asset-immutable");
        assertThat(finalized.getBytes()).isEqualTo(2048L);
    }

    @Test
    void directFinalize_ShouldClaimExpiredIntentWhenGraceEndsDuringProviderInspection() {
        Instant beforeInspection = Instant.parse("2026-10-15T03:00:00Z");
        clock.setInstant(beforeInspection);
        LocalDateTime now = LocalDateTime.ofInstant(beforeInspection, java.time.ZoneOffset.UTC);
        LocalDateTime issued = now.minusMinutes(55);
        Media pending = Media.createSignedPendingUpload(
                "late-finalize",
                "product/late-finalize",
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                2048,
                now.plusHours(1),
                issued,
                issued.plusHours(1));
        mediaRepository.save(pending);
        var snapshot = new MediaDirectUploadPersistence.Snapshot(
                pending.getId(),
                pending.getExternalId(),
                42L,
                pending.getPurpose(),
                pending.getUploadStatus(),
                pending.getProcessingStatus(),
                pending.getAttachmentStatus(),
                pending.getUrl(),
                pending.getAssetId(),
                pending.getIntentExpiresAt(),
                pending.getUploadAuthorizationIssuedAt(),
                pending.getUploadAuthorizationExpiresAt());

        // The provider call occurs between snapshot and finalize; its latency crosses the completion grace.
        clock.setInstant(beforeInspection.plusSeconds(15 * 60L + 1));
        var result = directUploadPersistence.finalizeUpload(
                snapshot, 42L, verifiedAsset(pending.getExternalId(), "late-asset"), "image/png");

        assertThat(result.uploadStatus()).isEqualTo(MediaUploadStatus.PENDING_UPLOAD);
        assertThat(result.attachmentStatus()).isEqualTo(MediaAttachmentStatus.PENDING_DELETE);
        Media stored = mediaRepository.findById(pending.getId()).orElseThrow();
        assertThat(stored.getAssetId()).isNull();
        assertThat(stored.getUploadStatus()).isEqualTo(MediaUploadStatus.PENDING_UPLOAD);
    }

    @Test
    void directDeleteAndFinalizeRace_ShouldNeverRevivePendingDelete() throws Exception {
        Media pending = pendingDirectMedia("direct-delete-race");
        mediaRepository.save(pending);
        var snapshot = new MediaDirectUploadPersistence.Snapshot(
                pending.getId(),
                pending.getExternalId(),
                42L,
                pending.getPurpose(),
                pending.getUploadStatus(),
                pending.getProcessingStatus(),
                pending.getAttachmentStatus(),
                null,
                null,
                pending.getIntentExpiresAt(),
                pending.getUploadAuthorizationIssuedAt(),
                pending.getUploadAuthorizationExpiresAt());
        MediaAssetInspector.InspectedMediaAsset asset = verifiedAsset(pending.getExternalId(), "asset-race");
        CountDownLatch start = new CountDownLatch(1);
        Future<?> complete = executor.submit(() -> {
            await(start);
            try {
                directUploadPersistence.finalizeUpload(snapshot, 42L, asset, "image/png");
            } catch (RuntimeException expectedWhenDeleteWins) {
                // The delete claim is a terminal lifecycle state for complete.
            }
        });
        Future<?> delete = executor.submit(() -> {
            await(start);
            directUploadPersistence.delete(pending.getId(), 42L);
        });
        start.countDown();
        complete.get(5, TimeUnit.SECONDS);
        delete.get(5, TimeUnit.SECONDS);

        Media result = mediaRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.PENDING_DELETE);
    }

    private static Media pendingDirectMedia(String id) {
        LocalDateTime issued = LocalDateTime.now().minusMinutes(1).withNano(0);
        return Media.createSignedPendingUpload(
                id,
                "product/" + id,
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                2048,
                LocalDateTime.now().plusHours(1),
                issued,
                issued.plusHours(1));
    }

    private static MediaAssetInspector.InspectedMediaAsset verifiedAsset(String publicId, String assetId) {
        return new MediaAssetInspector.InspectedMediaAsset(
                publicId,
                assetId,
                "image",
                "upload",
                "png",
                "https://cdn.example.test/direct.png",
                2048,
                800,
                600,
                LocalDateTime.now(),
                java.util.Map.of("media_id", "unused", "purpose", "PRODUCT_IMAGE"));
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
