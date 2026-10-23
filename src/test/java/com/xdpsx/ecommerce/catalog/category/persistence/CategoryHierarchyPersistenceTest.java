package com.xdpsx.ecommerce.catalog.category.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CreateCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.api.dto.MoveCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.api.dto.ReorderCategoriesRequest;
import com.xdpsx.ecommerce.catalog.category.api.dto.UpdateCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.application.CategoryHierarchy;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.catalog.category.application.CategoryServiceImpl;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.application.attachment.MediaAttachmentService;
import com.xdpsx.ecommerce.media.application.delivery.MediaUrlResolver;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

/**
 * Runs the Category hierarchy write queries against a real MySQL server.
 *
 * <p>These queries cannot be protected by an H2 substitute or a mocked repository: the ordered sibling lock relies on
 * {@code ORDER BY (parent_id, display_order)} plus {@code SELECT ... FOR UPDATE}, the root group and empty groups are
 * coordinated by a transaction-scoped anchor row. The concurrent cases below exercise hierarchy serialization,
 * metadata row locking, optimistic version checks and actor auditing on MySQL.
 *
 * <p>The schema is built by the real Liquibase changesets rather than by Hibernate, so the queries run against the same
 * {@code idx_category_parent_order} index and restrictive parent FK as production. The Category aggregate associates
 * with {@code media}, so both packages must be scanned.
 */
@Testcontainers
@SpringJUnitConfig(CategoryHierarchyPersistenceTest.PersistenceConfig.class)
class CategoryHierarchyPersistenceTest {

    private static final ThreadLocal<Long> TEST_ACTOR = ThreadLocal.withInitial(() -> 42L);

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create(
            "category_hierarchy", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing(auditorAwareRef = "testAuditor", modifyOnCreate = true)
    @EnableJpaRepositories(basePackageClasses = {CategoryRepository.class, MediaRepository.class})
    static class PersistenceConfig {

        /**
         * A real connection pool, matching production. A {@code DriverManagerDataSource} would close the physical
         * connection after every transaction and therefore release a session-scoped lock by accident, hiding exactly the
         * lock-leak this test class is meant to catch.
         */
        @Bean
        javax.sql.DataSource dataSource() {
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(jdbcUrlWithShortLockTimeout());
            config.setUsername(MYSQL.getUsername());
            config.setPassword(MYSQL.getPassword());
            config.setMaximumPoolSize(4);
            return new com.zaxxer.hikari.HikariDataSource(config);
        }

        /**
         * Keeps the lock-contention test fast and deterministic: the MySQL default is 50 seconds, which would make a
         * failing lock assertion look like a hung test instead of a rejected one.
         */
        private static String jdbcUrlWithShortLockTimeout() {
            String separator = MYSQL.getJdbcUrl().contains("?") ? "&" : "?";
            return MYSQL.getJdbcUrl() + separator + "sessionVariables=innodb_lock_wait_timeout=2";
        }

        /**
         * Builds the schema from the active master changelog, so query-plan and lock assertions reflect the fresh-install
         * production schema.
         */
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
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.catalog.category.domain", "com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            // No DDL from Hibernate: the schema must come from Liquibase, otherwise the index under test is not real.
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "none");
            // Boot's naming strategy is not applied outside a Boot context, so `createdAt` would not resolve to the
            // `created_at` column that the changesets create.
            factory.getJpaPropertyMap()
                    .put(
                            "hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            factory.getJpaPropertyMap().put("hibernate.show_sql", "false");
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        /**
         * The real service, so the concurrent cases run the reviewed lock path instead of a copy of it.
         *
         * <p>The retry budget is set to one attempt on purpose: with retries enabled, a deadlock would be retried into
         * a pass and the group anchor would look optional. A single attempt makes these tests fail if two writers are
         * ever not serialized by the anchor.
         */
        @Bean
        MediaAttachmentService mediaAttachmentService(
                MediaRepository mediaRepository, jakarta.persistence.EntityManager entityManager) {
            return new MediaAttachmentService(mediaRepository, () -> 42L, entityManager);
        }

        @Bean
        com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider currentUserIdProvider() {
            return TEST_ACTOR::get;
        }

        @Bean
        org.springframework.data.domain.AuditorAware<Long> testAuditor() {
            return () -> java.util.Optional.of(TEST_ACTOR.get());
        }

        @Bean
        UserRepository userRepository() {
            return org.mockito.Mockito.mock(UserRepository.class);
        }

        @Bean
        CategoryServiceImpl categoryService(
                CategoryRepository categoryRepository,
                MediaAttachmentService mediaAttachmentService,
                com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider currentUserIdProvider,
                UserRepository userRepository,
                TransactionTemplate transactionTemplate) {
            return new CategoryServiceImpl(
                    categoryRepository,
                    mediaAttachmentService,
                    new MediaUrlResolver((externalId, purpose) -> java.util.Map.of()),
                    new CategoryHierarchy(categoryRepository, 1),
                    currentUserIdProvider,
                    userRepository,
                    transactionTemplate);
        }
    }

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private javax.sql.DataSource dataSource;

    // Injected as the interface: @EnableTransactionManagement proxies the bean, so the concrete type is not
    // resolvable for injection.
    @Autowired
    private CategoryService categoryService;

    @BeforeEach
    void seed() {
        TEST_ACTOR.set(42L);
        transactionTemplate.execute(status -> {
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
            return null;
        });
    }

    @AfterEach
    void cleanup() {
        TEST_ACTOR.set(42L);
        transactionTemplate.execute(status -> {
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
            return null;
        });
    }

    private Integer persist(String name, Integer displayOrder, Integer parentId) {
        return transactionTemplate.execute(status -> {
            Category category = Category.builder()
                    .name(name)
                    .slug(name.toLowerCase(java.util.Locale.ROOT))
                    .status(CategoryStatus.ACTIVE)
                    .displayOrder(displayOrder)
                    .parent(parentId == null ? null : categoryRepository.getReferenceById(parentId))
                    .build();
            return categoryRepository.saveAndFlush(category).getId();
        });
    }

    private Long version(Integer categoryId) {
        return transactionTemplate.execute(
                status -> categoryRepository.findById(categoryId).orElseThrow().getVersion());
    }

    private String persistTemporaryCategoryImage(String id) {
        return transactionTemplate.execute(status -> mediaRepository
                .save(Media.createUploaded(
                        id,
                        "external-" + id,
                        "https://cdn.example.test/" + id + ".png",
                        null,
                        "image/png",
                        MediaPurpose.CATEGORY_IMAGE,
                        42L,
                        MediaAttachmentStatus.TEMPORARY,
                        null))
                .getId());
    }

    private Media media(String id) {
        return transactionTemplate.execute(
                status -> mediaRepository.findById(id).orElseThrow());
    }

    @Test
    void categoryVersion_ShouldStartAtZeroAndAdvanceForMetadataMoveAndReorderWrites() {
        Integer parentId = persist("Version Parent", 0, null);
        Integer otherRootId = persist("Version Other Root", 1, null);
        Integer firstChildId = persist("Version First Child", 0, parentId);
        Integer secondChildId = persist("Version Second Child", 1, parentId);

        assertThat(version(parentId)).isZero();
        assertThat(version(otherRootId)).isZero();
        assertThat(version(firstChildId)).isZero();
        assertThat(version(secondChildId)).isZero();

        AdminCategoryResponse updated = categoryService.updateCategory(
                firstChildId,
                new UpdateCategoryRequest("Version First Child Renamed", CategoryStatus.ACTIVE, null, null, 0L));
        assertThat(updated.version()).isEqualTo(1L);

        AdminCategoryResponse moved = categoryService.moveCategory(secondChildId, new MoveCategoryRequest(null, 0));
        assertThat(moved.version()).isEqualTo(1L);
        assertThat(version(parentId)).isEqualTo(1L); // Shifted from root position 0 to 1.
        assertThat(version(otherRootId)).isEqualTo(1L); // Shifted from root position 1 to 2.
        assertThat(version(firstChildId)).isEqualTo(1L); // Metadata update above.

        categoryService.reorderCategories(
                new ReorderCategoriesRequest(null, List.of(otherRootId, secondChildId, parentId)));

        assertThat(version(otherRootId)).isEqualTo(2L);
        assertThat(version(secondChildId)).isEqualTo(2L);
        assertThat(version(parentId)).isEqualTo(2L);
    }

    @Test
    void categoryVersion_ShouldRejectAStaleDatabaseWrite() {
        Integer categoryId = persist("Versioned Category", 0, null);
        Category stale = transactionTemplate.execute(
                status -> categoryRepository.findById(categoryId).orElseThrow());

        categoryService.updateCategory(
                categoryId, new UpdateCategoryRequest("Updated Category", CategoryStatus.ACTIVE, null, null, 0L));
        stale.setName("Stale Rename");

        assertThatThrownBy(() -> transactionTemplate.execute(status -> categoryRepository.saveAndFlush(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        String persistedName = transactionTemplate.execute(
                status -> categoryRepository.findById(categoryId).orElseThrow().getName());
        assertThat(persistedName).isEqualTo("Updated Category");
    }

    @Test
    void concurrentUpdatesWithSameVersionAndDifferentImages_ShouldAttachOnlyTheWinner() throws Exception {
        String originalImageId = persistTemporaryCategoryImage("original-category-image");
        String firstReplacementId = persistTemporaryCategoryImage("first-replacement-image");
        String secondReplacementId = persistTemporaryCategoryImage("second-replacement-image");
        AdminCategoryResponse created = categoryService.createCategory(
                new CreateCategoryRequest("Concurrent Image Category", CategoryStatus.ACTIVE, originalImageId, null));

        List<Integer> outcomes = runConcurrently(
                () -> updateCategoryWithImage(
                        created.id(), created.version(), "Concurrent Image A", firstReplacementId),
                () -> updateCategoryWithImage(
                        created.id(), created.version(), "Concurrent Image B", secondReplacementId));

        assertThat(outcomes).containsExactlyInAnyOrder(1, -1);
        Category persisted = transactionTemplate.execute(
                status -> categoryRepository.findById(created.id()).orElseThrow());
        String winningImageId = persisted.getImage().getId();
        String losingImageId = winningImageId.equals(firstReplacementId) ? secondReplacementId : firstReplacementId;

        assertThat(persisted.getVersion()).isEqualTo(created.version() + 1);
        assertThat(persisted.getName()).isIn("Concurrent Image A", "Concurrent Image B");
        assertThat(media(originalImageId).getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.PENDING_DELETE);
        assertThat(media(winningImageId).getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.ACTIVE);
        assertThat(media(losingImageId).getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.TEMPORARY);
    }

    private Integer updateCategoryWithImage(Integer categoryId, Long version, String name, String imageId) {
        try {
            categoryService.updateCategory(
                    categoryId, new UpdateCategoryRequest(name, CategoryStatus.ACTIVE, null, imageId, version));
            return 1;
        } catch (ApplicationException exception) {
            if (exception.getCode() == ErrorCode.CONCURRENT_MODIFICATION) return -1;
            throw exception;
        }
    }

    @Test
    void categoryActorAudit_ShouldPersistCreatorAndLastEditorFromTheAuthenticatedActor() {
        TEST_ACTOR.set(17L);
        AdminCategoryResponse created = categoryService.createCategory(
                new CreateCategoryRequest("Audited Category", CategoryStatus.ACTIVE, null, null));
        assertThat(created.createdBy()).isEqualTo(17L);
        assertThat(created.updatedBy()).isEqualTo(17L);
        assertThat(created.createdAt()).isNotNull();

        TEST_ACTOR.set(23L);
        categoryService.updateCategory(
                created.id(),
                new UpdateCategoryRequest(
                        "Audited Category Renamed", CategoryStatus.ACTIVE, null, null, created.version()));

        Category persisted = transactionTemplate.execute(
                status -> categoryRepository.findById(created.id()).orElseThrow());
        assertThat(persisted.getCreatedBy()).isEqualTo(17L);
        assertThat(persisted.getUpdatedBy()).isEqualTo(23L);
        assertThat(persisted.getCreatedAt()).isEqualTo(created.createdAt());
        assertThat(persisted.getUpdatedAt()).isNotNull();

        TEST_ACTOR.set(31L);
        assertThatThrownBy(() -> categoryService.updateCategory(
                        created.id(),
                        new UpdateCategoryRequest("Stale Actor Draft", CategoryStatus.ACTIVE, null, null, 0L)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        Category afterStaleWrite = transactionTemplate.execute(
                status -> categoryRepository.findById(created.id()).orElseThrow());
        assertThat(afterStaleWrite.getName()).isEqualTo("Audited Category Renamed");
        assertThat(afterStaleWrite.getUpdatedBy()).isEqualTo(23L);
    }

    @Test
    void hierarchyRenumbering_ShouldAuditEverySiblingActuallyChanged() {
        Integer first = persist("Audit First", 0, null);
        Integer second = persist("Audit Second", 1, null);

        TEST_ACTOR.set(77L);
        categoryService.moveCategory(second, new MoveCategoryRequest(null, 0));

        Map<Integer, Category> categories =
                transactionTemplate.execute(status -> categoryRepository.findAllById(List.of(first, second)).stream()
                        .collect(java.util.stream.Collectors.toMap(Category::getId, category -> category)));
        assertThat(categories.get(first).getDisplayOrder()).isEqualTo(1);
        assertThat(categories.get(second).getDisplayOrder()).isEqualTo(0);
        assertThat(categories.get(first).getUpdatedBy()).isEqualTo(77L);
        assertThat(categories.get(second).getUpdatedBy()).isEqualTo(77L);
        assertThat(categories.get(first).getCreatedBy()).isEqualTo(42L);
    }

    @Test
    void legacyCategoryWithUnknownCreator_ShouldRemainEditableWithoutInventingCreatedBy() {
        Integer categoryId = persist("Legacy Category", 0, null);
        new JdbcTemplate(dataSource)
                .update("UPDATE categories SET created_by = NULL, updated_by = NULL WHERE id = ?", categoryId);

        TEST_ACTOR.set(88L);
        categoryService.updateCategory(
                categoryId, new UpdateCategoryRequest("Legacy Category Edited", CategoryStatus.ACTIVE, null, null, 0L));

        Category persisted = transactionTemplate.execute(
                status -> categoryRepository.findById(categoryId).orElseThrow());
        assertThat(persisted.getCreatedBy()).isNull();
        assertThat(persisted.getUpdatedBy()).isEqualTo(88L);
    }

    private List<String> namesOf(List<Category> categories) {
        return categories.stream().map(Category::getName).toList();
    }

    @Test
    void findRootsForUpdate_ShouldReturnTheRootGroupInDisplayOrder() {
        Integer third = persist("Third", 2, null);
        Integer first = persist("First", 0, null);
        Integer second = persist("Second", 1, null);
        // A child must never leak into the root group.
        persist("Child", 0, first);

        List<Category> roots = transactionTemplate.execute(status -> categoryRepository.findRootsForUpdate());

        assertThat(namesOf(roots)).containsExactly("First", "Second", "Third");
        assertThat(roots).extracting(Category::getId).containsExactly(first, second, third);
    }

    @Test
    void findChildrenForUpdate_ShouldReturnOnlyThatParentsChildrenInDisplayOrder() {
        Integer parentId = persist("Electronics", 0, null);
        Integer otherParentId = persist("Fashion", 1, null);
        persist("Phones", 1, parentId);
        persist("Laptops", 0, parentId);
        persist("Shirts", 0, otherParentId);

        List<Category> children =
                transactionTemplate.execute(status -> categoryRepository.findChildrenForUpdate(parentId));

        assertThat(namesOf(children)).containsExactly("Laptops", "Phones");
    }

    @Test
    void siblingOrderTies_ShouldFallBackToIdForAStableOrder() {
        Integer parentId = persist("Electronics", 0, null);
        Integer firstTie = persist("TieA", 0, parentId);
        Integer secondTie = persist("TieB", 0, parentId);

        List<Category> children =
                transactionTemplate.execute(status -> categoryRepository.findChildrenForUpdate(parentId));

        assertThat(children).extracting(Category::getId).containsExactly(firstTie, secondTie);
    }

    /**
     * The ancestor-chain walk must return a root row as {@code parentId == null} rather than as a missing row, which
     * a {@code SELECT c.parent} projection could not distinguish.
     */
    @Test
    void findParentViewForUpdate_ShouldReturnTheParentIdAndEndAtARoot() {
        Integer rootId = persist("Electronics", 0, null);
        Integer childId = persist("Laptops", 0, rootId);

        CategoryRepository.CategoryParentView child = transactionTemplate.execute(
                status -> categoryRepository.findParentViewForUpdate(childId).orElseThrow());
        CategoryRepository.CategoryParentView root = transactionTemplate.execute(
                status -> categoryRepository.findParentViewForUpdate(rootId).orElseThrow());

        assertThat(child.getId()).isEqualTo(childId);
        assertThat(child.getParentId()).isEqualTo(rootId);
        assertThat(root.getId()).isEqualTo(rootId);
        assertThat(root.getParentId()).isNull();
    }

    /**
     * The boundary the plan called out: an EMPTY root group has no row to lock, so nothing serializes the two inserts
     * unless the named root anchor does. Both requests must still succeed and end up on orders 0 and 1.
     */
    @Test
    void concurrentCreateIntoEmptyRootGroup_ShouldSerializeInsteadOfRacing() throws Exception {
        // Typed separately because the generic execute(...) return type is ambiguous inside assertThat(...).
        List<Category> groupBeforeAnyCreate =
                transactionTemplate.execute(status -> categoryRepository.findRootsForUpdate());
        assertThat(groupBeforeAnyCreate).isEmpty();

        List<Integer> assignedOrders =
                runConcurrently(() -> createRootCategory("EmptyRootA"), () -> createRootCategory("EmptyRootB"));

        assertThat(assignedOrders).containsExactlyInAnyOrder(0, 1);

        List<Category> roots = transactionTemplate.execute(status -> categoryRepository.findRootsForUpdate());
        assertThat(roots).hasSize(2);
        assertThat(roots).extracting(Category::getDisplayOrder).containsExactly(0, 1);
    }

    /**
     * Two creates into a non-empty root group. The loser must wait for the winner's lock and then observe the winner's
     * row, which is exactly what {@code MAX(display_order) + 1} could not guarantee.
     */
    @Test
    void concurrentAppend_ShouldNeverAssignDuplicateRootOrder() throws Exception {
        Integer existing = persist("First", 0, null);

        List<Integer> assignedOrders =
                runConcurrently(() -> createRootCategory("ConcurrentA"), () -> createRootCategory("ConcurrentB"));

        // The pre-existing root keeps order 0, so the two appends take 1 and 2. The order 2 result is the proof that
        // one transaction waited and then saw the other's row instead of taking the same free slot.
        assertThat(assignedOrders).containsExactlyInAnyOrder(1, 2);

        List<Category> roots = transactionTemplate.execute(status -> categoryRepository.findRootsForUpdate());
        assertThat(roots).hasSize(3);
        assertThat(roots).extracting(Category::getId).contains(existing);
        assertThat(roots).extracting(Category::getDisplayOrder).containsExactly(0, 1, 2);
    }

    /**
     * Two moves of different roots inside the same root sibling group. Both lock the whole group, so this is the case
     * the row-by-row lock order alone could not rule out; the shared group anchor must serialize them.
     */
    @Test
    void concurrentMoveInsideTheSameGroup_ShouldKeepAContiguousOrder() throws Exception {
        Integer first = persist("First", 0, null);
        Integer second = persist("Second", 1, null);
        Integer third = persist("Third", 2, null);

        runConcurrently(() -> moveRoot(first, 2), () -> moveRoot(second, 0));

        List<Category> roots = transactionTemplate.execute(status -> categoryRepository.findRootsForUpdate());
        assertThat(roots).extracting(Category::getDisplayOrder).containsExactly(0, 1, 2);
        assertThat(roots).extracting(Category::getId).containsExactlyInAnyOrder(first, second, third);
    }

    /**
     * A metadata update must not write back the parent/order it read before a concurrent move. The category row lock
     * serializes the writes, and the request version rejects a form made stale by whichever operation commits first.
     */
    @Test
    void concurrentUpdateAndMove_ShouldNotLoseTheMove() throws Exception {
        Integer parentA = persist("Electronics", 0, null);
        Integer parentB = persist("Fashion", 1, null);
        Integer categoryId = persist("Laptops", 0, parentA);

        List<Integer> outcomes = runConcurrently(
                () -> moveChild(categoryId, parentB, 0), () -> renameCategory(categoryId, "Laptops Renamed"));

        Category persisted = transactionTemplate.execute(
                status -> categoryRepository.findById(categoryId).orElseThrow());

        assertThat(persisted.getParent().getId()).isEqualTo(parentB);
        assertThat(persisted.getName()).isIn("Laptops Renamed", "Laptops");
        if (persisted.getName().equals("Laptops")) {
            assertThat(outcomes).contains(-1);
        }
    }

    @Test
    void updateMetadata_ShouldNotWaitForTheHierarchyAnchor() throws Exception {
        Integer categoryId = persist("Independent Metadata", 0, null);
        AtomicReference<Future<AdminCategoryResponse>> update = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            transactionTemplate.execute(status -> {
                categoryRepository.lockHierarchyAnchor();
                update.set(executor.submit(() -> categoryService.updateCategory(
                        categoryId,
                        new UpdateCategoryRequest(
                                "Independent Metadata Updated", CategoryStatus.ACTIVE, null, null, 0L))));
                try {
                    assertThat(update.get().get(5, TimeUnit.SECONDS).name()).isEqualTo("Independent Metadata Updated");
                } catch (Exception exception) {
                    throw new AssertionError(
                            "Metadata update should not be blocked by the hierarchy anchor", exception);
                }
                status.setRollbackOnly();
                return null;
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private Integer moveChild(Integer categoryId, Integer parentId, int position) {
        return categoryService
                .moveCategory(categoryId, new MoveCategoryRequest(parentId, position))
                .displayOrder();
    }

    /** Returns the order so it shares the {@code Callable<Integer>} concurrency helper; the caller asserts the name. */
    private Integer renameCategory(Integer categoryId, String newName) {
        Category current = transactionTemplate.execute(
                status -> categoryRepository.findById(categoryId).orElseThrow());
        // Use the version read with the category. If a move wins before the update obtains its
        // lock, the stale update must be rejected instead of writing the old parent back.
        UpdateCategoryRequest request =
                new UpdateCategoryRequest(newName, current.getStatus(), null, null, current.getVersion());
        try {
            return categoryService.updateCategory(categoryId, request).displayOrder();
        } catch (ApplicationException exception) {
            if (exception.getCode() == ErrorCode.CONCURRENT_MODIFICATION) {
                return -1;
            }
            throw exception;
        }
    }

    private Integer createRootCategory(String name) {
        return categoryService
                .createCategory(new CreateCategoryRequest(name, CategoryStatus.ACTIVE, null, null))
                .displayOrder();
    }

    private Integer moveRoot(Integer categoryId, int position) {
        return categoryService
                .moveCategory(categoryId, new MoveCategoryRequest(null, position))
                .displayOrder();
    }

    /**
     * Runs both tasks after a barrier so the lock contention is real. A failed transaction is surfaced as an exception
     * instead of being hidden, so a broken lock cannot pass silently.
     */
    private List<Integer> runConcurrently(Callable<Integer> firstTask, Callable<Integer> secondTask) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return firstTask.call();
            });
            Future<Integer> second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return secondTask.call();
            });
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The anchor must be released by commit, which is the whole reason it is a row lock and not {@code GET_LOCK}. The
     * datasource is a pool (HikariCP) in this test, so a session-scoped lock would stay held on the pooled connection
     * after the transaction and the competing transaction below would block until it timed out.
     */
    @Test
    void lockHierarchyAnchor_ShouldBeReleasedByCommit() throws Exception {
        assertAnchorIsFreeAfterTransaction(true);
    }

    /** Same contract for a rolled-back transaction: the rollback must release the anchor as well. */
    @Test
    void lockHierarchyAnchor_ShouldBeReleasedByRollback() throws Exception {
        assertAnchorIsFreeAfterTransaction(false);
    }

    private void assertAnchorIsFreeAfterTransaction(boolean commit) {
        transactionTemplate.execute(status -> {
            categoryRepository.lockHierarchyAnchor();
            if (!commit) status.setRollbackOnly();
            return null;
        });

        // Must complete promptly: a lock still held on the pooled connection would fail this with a timeout.
        Integer result = transactionTemplate.execute(status -> {
            categoryRepository.lockHierarchyAnchor();
            return 1;
        });

        assertThat(result).isEqualTo(1);
    }

    @Test
    void lockHierarchyAnchor_ShouldSerializeTwoTransactions() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Boolean>> competingAttempt = new AtomicReference<>();
        try {
            transactionTemplate.execute(status -> {
                categoryRepository.lockHierarchyAnchor();

                // Started while the anchor is held, so it must block instead of proceeding.
                competingAttempt.set(executor.submit(() -> {
                    transactionTemplate.execute(inner -> {
                        categoryRepository.lockHierarchyAnchor();
                        return null;
                    });
                    return true;
                }));

                assertThatThrownBy(() -> competingAttempt.get().get(1, TimeUnit.SECONDS))
                        .isInstanceOf(TimeoutException.class);
                return null;
            });

            // Committing the holder releases the anchor, so the waiting transaction now finishes.
            assertThat(competingAttempt.get().get(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void findByIdForUpdate_ShouldTakeAWriteLockThatBlocksACompetingTransaction() {
        Integer id = persist("First", 0, null);

        transactionTemplate.execute(status -> {
            categoryRepository.findByIdForUpdate(id);

            // The competing transaction must run on another thread: a nested execute() on this thread would simply
            // join the surrounding transaction and see no contention at all.
            String lockFailure = runCompetingUpdate(id);
            assertThat(lockFailure).contains("Lock wait timeout");

            status.setRollbackOnly();
            return null;
        });
    }

    /**
     * Attempts to lock and update the same row from a second connection. The transaction outcome is returned instead
     * of thrown, so the assertion stays readable and a lock timeout is not mistaken for a broken test.
     */
    private String runCompetingUpdate(Integer id) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(() -> {
                        try {
                            transactionTemplate.execute(status -> {
                                Category sameRow =
                                        categoryRepository.findByIdForUpdate(id).orElseThrow();
                                sameRow.setName("Renamed");
                                return categoryRepository.saveAndFlush(sameRow);
                            });
                            return null;
                        } catch (RuntimeException exception) {
                            return exception.getMessage();
                        }
                    })
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("The competing transaction could not be started", exception);
        } finally {
            executor.shutdownNow();
        }
    }
}
