package com.xdpsx.ecommerce.catalog.category.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CreateCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.api.dto.MoveCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.api.dto.ReorderCategoriesRequest;
import com.xdpsx.ecommerce.catalog.category.api.dto.UpdateCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/**
 * Guards the Category write behavior: create appends to the locked sibling group, move/reorder maintain the sibling
 * order invariants, and delete owns the Media lifecycle.
 *
 * <p>The real {@link CategoryHierarchy} is used rather than a mock, so the cycle, subtree-height and position rules
 * are exercised as written. Only the repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MediaRepository mediaRepository;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        // Runs each attempt inline: transaction mechanics are covered by the MySQL persistence test, while this test
        // isolates the business rules.
        TransactionOperations inlineTransaction = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        categoryService = new CategoryServiceImpl(
                categoryRepository, mediaRepository, new CategoryHierarchy(categoryRepository), inlineTransaction);
        // Every hierarchy write takes the anchor first. Lenient because the read-only cases never reach it.
        lenient().when(categoryRepository.hierarchyAnchorExists()).thenReturn(true);
    }

    private static Category category(Integer id, String name, String slug, Integer displayOrder) {
        return Category.builder()
                .id(id)
                .name(name)
                .slug(slug)
                .status(CategoryStatus.INACTIVE)
                .displayOrder(displayOrder)
                .build();
    }

    /** Builds the {@code id -> parentId} projection that the subtree-height check reads. */
    private static List<CategoryRepository.CategoryParentView> parentViews(Map<Integer, Integer> parents) {
        return parents.entrySet().stream()
                .map(entry -> {
                    CategoryRepository.CategoryParentView view = mock(CategoryRepository.CategoryParentView.class);
                    when(view.getId()).thenReturn(entry.getKey());
                    when(view.getParentId()).thenReturn(entry.getValue());
                    return view;
                })
                .toList();
    }

    /**
     * Stubs the locked ancestor-chain walk with alternating {@code id, parentId} pairs. The first pair is the node
     * whose chain is being locked; a trailing {@code null} parent ends the chain.
     */
    private void stubAncestorChain(Integer... idAndParentPairs) {
        parents(idAndParentPairs).forEach((id, parentId) -> {
            CategoryRepository.CategoryParentView view = parentView(parentId);
            when(categoryRepository.findParentViewForUpdate(id)).thenReturn(Optional.of(view));
        });
    }

    /** Chain-walk projection: only {@code parentId} is read, so only that getter is stubbed. */
    private static CategoryRepository.CategoryParentView parentView(Integer parentId) {
        CategoryRepository.CategoryParentView view = mock(CategoryRepository.CategoryParentView.class);
        when(view.getParentId()).thenReturn(parentId);
        return view;
    }

    /**
     * Alternating {@code id, parentId} pairs. A helper is needed because {@code Map.of} rejects the {@code null}
     * parent used by root categories.
     */
    private static Map<Integer, Integer> parents(Integer... idAndParentPairs) {
        Map<Integer, Integer> parents = new HashMap<>();
        for (int index = 0; index < idAndParentPairs.length; index += 2) {
            parents.put(idAndParentPairs[index], idAndParentPairs[index + 1]);
        }
        return parents;
    }

    private static List<Integer> orders(List<Category> categories) {
        return categories.stream().map(Category::getDisplayOrder).toList();
    }

    @Test
    void createRootCategory_ShouldNormalizeSlugAndAppendToLockedRootSiblings() {
        // Arrange: the stored orders are already gapped/duplicated, which the normalization must repair.
        CreateCategoryRequest request =
                new CreateCategoryRequest("Đồ chơi & Trẻ em", CategoryStatus.ACTIVE, null, null);
        List<Category> roots =
                new ArrayList<>(List.of(category(1, "A", "a", 3), category(2, "B", "b", 7), category(3, "C", "c", 8)));

        when(categoryRepository.existsByName(request.name())).thenReturn(false);
        when(categoryRepository.existsBySlug("do-choi-tre-em")).thenReturn(false);
        when(categoryRepository.findRootsForUpdate()).thenReturn(roots);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AdminCategoryResponse response = categoryService.createCategory(request);

        // Assert
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category saved = captor.getValue();

        assertEquals("do-choi-tre-em", saved.getSlug());
        assertEquals(CategoryStatus.ACTIVE, saved.getStatus());
        // The group is renumbered from the locked rows and the new node takes the first free index.
        assertEquals(List.of(0, 1, 2), orders(roots));
        assertEquals(3, saved.getDisplayOrder());
        assertNull(saved.getParent());
        assertEquals("do-choi-tre-em", response.slug());
    }

    @Test
    void createChildCategory_ShouldAppendToParentSiblingsAndActivateTemporaryImage() {
        // Arrange
        String imageId = "media-id";
        Category parent = Category.builder().id(7).name("Electronics").build();
        Media media = Media.builder()
                .id(imageId)
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .status(MediaStatus.TEMPORARY)
                .build();
        CreateCategoryRequest request = new CreateCategoryRequest("Laptops", CategoryStatus.ACTIVE, imageId, 7);

        when(categoryRepository.existsByName("Laptops")).thenReturn(false);
        when(categoryRepository.existsBySlug("laptops")).thenReturn(false);
        when(categoryRepository.findByIdWithParent(7)).thenReturn(Optional.of(parent));
        stubAncestorChain(7, null);
        when(categoryRepository.findChildrenForUpdate(7))
                .thenReturn(new ArrayList<>(List.of(category(8, "Phones", "phones", 0))));
        when(mediaRepository.findAttachableById(imageId, MediaPurpose.CATEGORY_IMAGE))
                .thenReturn(Optional.of(media));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.createCategory(request);

        // Assert
        assertEquals(MediaStatus.ACTIVE, media.getStatus());

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertSame(parent, captor.getValue().getParent());
        assertEquals(1, captor.getValue().getDisplayOrder());
        assertSame(media, captor.getValue().getImage());
    }

    @Test
    void createCategory_ShouldOmitStatus_WhenStatusIsNotProvided() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Shoes", null, null, null);
        when(categoryRepository.existsByName("Shoes")).thenReturn(false);
        when(categoryRepository.existsBySlug("shoes")).thenReturn(false);
        when(categoryRepository.findRootsForUpdate()).thenReturn(new ArrayList<>());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.createCategory(request);

        // Assert
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertEquals(CategoryStatus.INACTIVE, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getDisplayOrder());
    }

    @Test
    void createCategory_ShouldRejectAndNotSave_WhenNameNormalizesToAnEmptySlug() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("!!!", CategoryStatus.ACTIVE, null, null);
        when(categoryRepository.existsByName("!!!")).thenReturn(false);

        // Act & Assert
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> categoryService.createCategory(request));

        assertEquals(ErrorCode.INVALID_CATEGORY_SLUG, exception.getCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_ShouldReject_WhenGeneratedSlugAlreadyExists() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Giày dép", CategoryStatus.ACTIVE, null, null);
        when(categoryRepository.existsByName("Giày dép")).thenReturn(false);
        when(categoryRepository.existsBySlug("giay-dep")).thenReturn(true);

        // Act & Assert
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> categoryService.createCategory(request));

        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        assertEquals("slug", exception.getParameters().get("field"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_ShouldReject_WhenParentIsAlreadyAtMaximumDepth() {
        // Arrange: the parent is a level-3 node, so a child would be level 4.
        CreateCategoryRequest request = new CreateCategoryRequest("Cables", CategoryStatus.ACTIVE, null, 3);
        Category levelThreeParent = Category.builder()
                .id(3)
                .name("Laptops")
                .status(CategoryStatus.ACTIVE)
                .build();

        when(categoryRepository.existsByName("Cables")).thenReturn(false);
        when(categoryRepository.existsBySlug("cables")).thenReturn(false);
        when(categoryRepository.findByIdWithParent(3)).thenReturn(Optional.of(levelThreeParent));
        stubAncestorChain(3, 2, 2, 1, 1, null);

        // Act & Assert
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> categoryService.createCategory(request));

        assertEquals(ErrorCode.INVALID_CATEGORY_DEPTH, exception.getCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_ShouldKeepSlug_WhenNameChangesWithoutExplicitSlug() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setUpdatedAt(updatedAt);
        UpdateCategoryRequest request =
                new UpdateCategoryRequest("Sneakers", CategoryStatus.ACTIVE, null, null, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByName("Sneakers")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AdminCategoryResponse response = categoryService.updateCategory(categoryId, request);

        // Assert
        assertEquals("Sneakers", response.name());
        assertEquals("shoes", response.slug());
        assertEquals(CategoryStatus.ACTIVE, response.status());
        verify(categoryRepository, never()).existsBySlug(any());
    }

    @Test
    void updateCategory_ShouldChangeSlug_WhenExplicitSlugIsValidAndUnique() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setUpdatedAt(updatedAt);
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Shoes", CategoryStatus.ACTIVE, "giay-the-thao", null, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsBySlug("giay-the-thao")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AdminCategoryResponse response = categoryService.updateCategory(categoryId, request);

        // Assert
        assertEquals("giay-the-thao", response.slug());
    }

    @Test
    void updateCategory_ShouldNotTouchParentOrOrder() {
        // Arrange: the category already has a parent and an order that the metadata update must preserve.
        int categoryId = 5;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category parent = Category.builder()
                .id(9)
                .name("Retired")
                .status(CategoryStatus.INACTIVE)
                .build();
        Category category = category(categoryId, "Shoes", "shoes", 4);
        category.setParent(parent);
        category.setUpdatedAt(updatedAt);
        UpdateCategoryRequest request =
                new UpdateCategoryRequest("Shoes", CategoryStatus.ACTIVE, null, null, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.updateCategory(categoryId, request);

        // Assert
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertSame(parent, captor.getValue().getParent());
        assertEquals(4, captor.getValue().getDisplayOrder());
        // Moving a node requires the dedicated operation, so update never locks or renumbers a sibling group.
        verify(categoryRepository, never()).findRootsForUpdate();
        verify(categoryRepository, never()).findChildrenForUpdate(any());
    }

    @Test
    void updateCategory_ShouldReplaceImage_ActivatingNewAndPendingDeletingOld() {
        // Arrange
        int categoryId = 1;
        String oldImageId = "old-media";
        String newImageId = "new-media";
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);

        Media oldImage = Media.builder()
                .id(oldImageId)
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .status(MediaStatus.ACTIVE)
                .build();
        Media newImage = Media.builder()
                .id(newImageId)
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .status(MediaStatus.TEMPORARY)
                .build();
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setImage(oldImage);
        category.setUpdatedAt(updatedAt);

        UpdateCategoryRequest request =
                new UpdateCategoryRequest("Shoes", CategoryStatus.ACTIVE, null, newImageId, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(category));
        when(mediaRepository.findAttachableById(newImageId, MediaPurpose.CATEGORY_IMAGE))
                .thenReturn(Optional.of(newImage));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.updateCategory(categoryId, request);

        // Assert
        assertEquals(MediaStatus.PENDING_DELETE, oldImage.getStatus());
        assertEquals(MediaStatus.ACTIVE, newImage.getStatus());

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertSame(newImage, captor.getValue().getImage());
        verify(mediaRepository, times(2)).save(any(Media.class));
    }

    @Test
    void moveCategory_ShouldMoveSubtreeToPositionInAnotherGroup_AndNormalizeBothGroups() {
        // Arrange: move node 5 (the second root) under parent 20 at position 1.
        Category moved = category(5, "Laptops", "laptops", 1);
        Category targetParent = Category.builder().id(20).name("Electronics").build();
        Category oldSiblingA = category(4, "Fashion", "fashion", 0);
        Category oldSiblingB = category(6, "Books", "books", 2);
        Category newSiblingA = category(21, "Phones", "phones", 0);
        Category newSiblingB = category(22, "Cameras", "cameras", 1);

        when(categoryRepository.findByIdForUpdate(5)).thenReturn(Optional.of(moved));
        when(categoryRepository.findByIdForUpdate(20)).thenReturn(Optional.of(targetParent));
        stubAncestorChain(20, null);
        List<CategoryRepository.CategoryParentView> parents =
                parentViews(parents(5, null, 4, null, 6, null, 20, null, 21, 20, 22, 20));
        when(categoryRepository.findAllParentViews()).thenReturn(parents);
        // The root group is locked first because roots sort before any parent id in the lock order.
        when(categoryRepository.findRootsForUpdate())
                .thenReturn(new ArrayList<>(List.of(oldSiblingA, moved, oldSiblingB)));
        when(categoryRepository.findChildrenForUpdate(20))
                .thenReturn(new ArrayList<>(List.of(newSiblingA, newSiblingB)));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.moveCategory(5, new MoveCategoryRequest(20, 1));

        // Assert: the gap in the old group is closed and the target group is renumbered around the moved node.
        assertEquals(List.of(0, 1), orders(List.of(oldSiblingA, oldSiblingB)));
        assertEquals(List.of(0, 1, 2), orders(List.of(newSiblingA, moved, newSiblingB)));
        assertSame(targetParent, moved.getParent());
        verify(categoryRepository).flush();
    }

    @Test
    void moveCategory_ShouldReorderInsideTheSameGroup_UsingRemoveThenInsertSemantics() {
        // Arrange: the group has 3 nodes; moving the first one to the last position must remain valid.
        Category parent = Category.builder().id(10).name("Electronics").build();
        Category moved = category(1, "A", "a", 0);
        moved.setParent(parent);
        Category siblingB = category(2, "B", "b", 1);
        Category siblingC = category(3, "C", "c", 2);

        when(categoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(moved));
        when(categoryRepository.findByIdForUpdate(10)).thenReturn(Optional.of(parent));
        stubAncestorChain(10, null);
        when(categoryRepository.findChildrenForUpdate(10))
                .thenReturn(new ArrayList<>(List.of(moved, siblingB, siblingC)));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.moveCategory(1, new MoveCategoryRequest(10, 2));

        // Assert: the moved node is removed first, so position 2 is the last index of the remaining two nodes.
        assertEquals(List.of(0, 1, 2), orders(List.of(siblingB, siblingC, moved)));
        assertSame(parent, moved.getParent());
        verify(categoryRepository).flush();
    }

    @Test
    void moveCategory_ShouldRejectSelfParent_WithoutTouchingAnyGroup() {
        // Arrange
        Category moved = category(1, "A", "a", 0);
        when(categoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(moved));

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> categoryService.moveCategory(1, new MoveCategoryRequest(1, 0)));

        assertEquals(ErrorCode.INVALID_CATEGORY_HIERARCHY, exception.getCode());
        assertEquals("self-parent", exception.getParameters().get("reason"));
        verify(categoryRepository, never()).findRootsForUpdate();
        verify(categoryRepository, never()).findChildrenForUpdate(any());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void moveCategory_ShouldRejectMovingUnderOwnDescendant() {
        // Arrange: 3 is a grandchild of 1, so 1 must not become a child of 3.
        Category moved = category(1, "A", "a", 0);
        Category descendant = Category.builder().id(3).name("C").build();

        when(categoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(moved));
        when(categoryRepository.findByIdForUpdate(3)).thenReturn(Optional.of(descendant));
        stubAncestorChain(3, 2, 2, 1, 1, null);

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> categoryService.moveCategory(1, new MoveCategoryRequest(3, 0)));

        assertEquals(ErrorCode.INVALID_CATEGORY_HIERARCHY, exception.getCode());
        assertEquals("descendant", exception.getParameters().get("reason"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void moveCategory_ShouldReject_WhenTheStoredHierarchyContainsACycle() {
        // Arrange: 1 and 2 are each other's parent, so the ancestor walk must terminate instead of looping forever.
        Category moved = category(1, "A", "a", 0);
        Category cyclic = Category.builder().id(2).name("B").build();

        when(categoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(moved));
        when(categoryRepository.findByIdForUpdate(2)).thenReturn(Optional.of(cyclic));
        stubAncestorChain(2, 1, 1, 2);

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> categoryService.moveCategory(1, new MoveCategoryRequest(2, 0)));

        assertEquals(ErrorCode.INVALID_CATEGORY_HIERARCHY, exception.getCode());
        assertEquals("cycle", exception.getParameters().get("reason"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void moveCategory_ShouldReject_WhenTheMovedSubtreeWouldExceedMaximumDepth() {
        // Arrange: the moved node has height 3 while its target parent already sits at level 2.
        Category moved = category(1, "A", "a", 0);
        Category targetParent = Category.builder().id(10).name("B").build();

        when(categoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(moved));
        when(categoryRepository.findByIdForUpdate(10)).thenReturn(Optional.of(targetParent));
        stubAncestorChain(10, 20, 20, null);
        List<CategoryRepository.CategoryParentView> parents =
                parentViews(parents(1, null, 2, 1, 3, 2, 10, 20, 20, null));
        when(categoryRepository.findAllParentViews()).thenReturn(parents);

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> categoryService.moveCategory(1, new MoveCategoryRequest(10, 0)));

        assertEquals(ErrorCode.INVALID_CATEGORY_DEPTH, exception.getCode());
        verify(categoryRepository, never()).findChildrenForUpdate(any());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void moveCategory_ShouldRejectPositionOutsideTheTargetGroup() {
        // Arrange: after removing the moved node the group has 2 nodes, so position 3 does not exist.
        Category parent = Category.builder().id(10).name("Electronics").build();
        Category moved = category(1, "A", "a", 0);
        moved.setParent(parent);

        when(categoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(moved));
        when(categoryRepository.findByIdForUpdate(10)).thenReturn(Optional.of(parent));
        stubAncestorChain(10, null);
        when(categoryRepository.findChildrenForUpdate(10))
                .thenReturn(new ArrayList<>(List.of(moved, category(2, "B", "b", 1), category(3, "C", "c", 2))));

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> categoryService.moveCategory(1, new MoveCategoryRequest(10, 3)));

        assertEquals(ErrorCode.INVALID_CATEGORY_ORDER, exception.getCode());
        assertEquals(2, exception.getParameters().get("maxPosition"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void reorderCategories_ShouldAssignOrderFromRequestIndexes() {
        // Arrange: request order 3, 1, 2 becomes displayOrder 0, 1, 2.
        Category first = category(1, "A", "a", 0);
        Category second = category(2, "B", "b", 1);
        Category third = category(3, "C", "c", 2);

        when(categoryRepository.findByIdWithParent(10))
                .thenReturn(Optional.of(
                        Category.builder().id(10).name("Electronics").build()));
        when(categoryRepository.findChildrenForUpdate(10)).thenReturn(new ArrayList<>(List.of(first, second, third)));

        // Act
        categoryService.reorderCategories(new ReorderCategoriesRequest(10, List.of(3, 1, 2)));

        // Assert
        assertEquals(0, third.getDisplayOrder());
        assertEquals(1, first.getDisplayOrder());
        assertEquals(2, second.getDisplayOrder());
        verify(categoryRepository).flush();
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void reorderCategories_ShouldRejectDuplicateIds() {
        // Arrange
        when(categoryRepository.findByIdWithParent(10))
                .thenReturn(Optional.of(
                        Category.builder().id(10).name("Electronics").build()));
        when(categoryRepository.findChildrenForUpdate(10))
                .thenReturn(new ArrayList<>(List.of(category(1, "A", "a", 0), category(2, "B", "b", 1))));

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> categoryService.reorderCategories(new ReorderCategoriesRequest(10, List.of(1, 1))));

        assertEquals(ErrorCode.INVALID_CATEGORY_ORDER, exception.getCode());
        verify(categoryRepository, never()).flush();
    }

    @Test
    void reorderCategories_ShouldReject_WhenTheListIsNotTheExactSiblingGroup() {
        // Arrange: the group has 3 nodes and the request omits one of them.
        when(categoryRepository.findByIdWithParent(10))
                .thenReturn(Optional.of(
                        Category.builder().id(10).name("Electronics").build()));
        when(categoryRepository.findChildrenForUpdate(10))
                .thenReturn(new ArrayList<>(
                        List.of(category(1, "A", "a", 0), category(2, "B", "b", 1), category(3, "C", "c", 2))));

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> categoryService.reorderCategories(new ReorderCategoriesRequest(10, List.of(1, 2))));

        assertEquals(ErrorCode.INVALID_CATEGORY_ORDER, exception.getCode());
        assertEquals(3, exception.getParameters().get("groupSize"));
        verify(categoryRepository, never()).flush();
    }

    @Test
    void deleteCategory_ShouldReject_WhenCategoryStillHasChildren() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setUpdatedAt(updatedAt);

        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(true);

        // Act & Assert
        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> categoryService.deleteCategory(categoryId, new ModifyExclusiveDTO(updatedAt.plusMinutes(1))));

        assertEquals(ErrorCode.RESOURCE_IN_USE, exception.getCode());
        verify(categoryRepository, never()).countCategoriesInOtherTables(any());
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deleteCategory_ShouldCloseTheGapInTheSiblingOrder() {
        // Arrange: deleting the middle node leaves orders 0 and 2 in the group.
        int categoryId = 2;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category parent = Category.builder().id(10).name("Electronics").build();
        Category first = category(1, "A", "a", 0);
        Category middle = category(categoryId, "B", "b", 1);
        middle.setParent(parent);
        middle.setUpdatedAt(updatedAt);
        Category last = category(3, "C", "c", 2);

        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(middle));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(categoryRepository.countCategoriesInOtherTables(categoryId)).thenReturn(0L);
        when(categoryRepository.findChildrenForUpdate(10)).thenReturn(new ArrayList<>(List.of(first, middle, last)));

        // Act
        categoryService.deleteCategory(categoryId, new ModifyExclusiveDTO(updatedAt.plusMinutes(1)));

        // Assert
        assertEquals(List.of(0, 1), orders(List.of(first, last)));
        verify(categoryRepository).delete(middle);
    }

    @Test
    void deleteCategory_ShouldMarkImagePendingDeletionAndDelete() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Media image = Media.builder()
                .id("media-id")
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .status(MediaStatus.ACTIVE)
                .build();
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setImage(image);
        category.setUpdatedAt(updatedAt);

        when(categoryRepository.findByIdForUpdate(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(categoryRepository.countCategoriesInOtherTables(categoryId)).thenReturn(0L);
        when(categoryRepository.findRootsForUpdate()).thenReturn(new ArrayList<>(List.of(category)));

        // Act
        categoryService.deleteCategory(categoryId, new ModifyExclusiveDTO(updatedAt.plusMinutes(1)));

        // Assert
        assertEquals(MediaStatus.PENDING_DELETE, image.getStatus());
        verify(mediaRepository).save(image);
        verify(categoryRepository).delete(category);
    }
}
