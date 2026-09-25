package com.xdpsx.ecommerce.catalog.category.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CreateCategoryRequest;
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
 * Guards the Category create/update/delete behavior introduced by the model and API boundary change, including the
 * Media lifecycle that Category owns.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private static Category category(Integer id, String name, String slug, Integer displayOrder) {
        return Category.builder()
                .id(id)
                .name(name)
                .slug(slug)
                .status(CategoryStatus.INACTIVE)
                .displayOrder(displayOrder)
                .build();
    }

    @Test
    void createRootCategory_ShouldNormalizeSlugAndAppendToRootSiblings() {
        // Arrange
        CreateCategoryRequest request =
                new CreateCategoryRequest("Đồ chơi & Trẻ em", CategoryStatus.ACTIVE, null, null);

        when(categoryRepository.existsByName(request.name())).thenReturn(false);
        when(categoryRepository.existsBySlug("do-choi-tre-em")).thenReturn(false);
        when(categoryRepository.findMaxDisplayOrderForRoots()).thenReturn(4);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AdminCategoryResponse response = categoryService.createCategory(request);

        // Assert
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category saved = captor.getValue();

        assertEquals("do-choi-tre-em", saved.getSlug());
        assertEquals(CategoryStatus.ACTIVE, saved.getStatus());
        assertEquals(5, saved.getDisplayOrder());
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
        when(categoryRepository.findMaxDisplayOrderByParentId(7)).thenReturn(2);
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
        assertEquals(3, captor.getValue().getDisplayOrder());
        assertSame(media, captor.getValue().getImage());
    }

    @Test
    void createCategory_ShouldOmitStatus_WhenStatusIsNotProvided() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Shoes", null, null, null);
        when(categoryRepository.existsByName("Shoes")).thenReturn(false);
        when(categoryRepository.existsBySlug("shoes")).thenReturn(false);
        when(categoryRepository.findMaxDisplayOrderForRoots()).thenReturn(-1);
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
    void updateCategory_ShouldKeepSlug_WhenNameChangesWithoutExplicitSlug() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setUpdatedAt(updatedAt);
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Sneakers", CategoryStatus.ACTIVE, null, null, null, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdWithParent(categoryId)).thenReturn(Optional.of(category));
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
                "Shoes", CategoryStatus.ACTIVE, "giay-the-thao", null, null, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdWithParent(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsBySlug("giay-the-thao")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AdminCategoryResponse response = categoryService.updateCategory(categoryId, request);

        // Assert
        assertEquals("giay-the-thao", response.slug());
    }

    @Test
    void updateCategory_ShouldAcceptInactiveParent_WhenParentChanges() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setUpdatedAt(updatedAt);
        Category inactiveParent = Category.builder()
                .id(9)
                .name("Retired")
                .status(CategoryStatus.INACTIVE)
                .build();
        UpdateCategoryRequest request =
                new UpdateCategoryRequest("Shoes", CategoryStatus.ACTIVE, null, null, 9, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdWithParent(categoryId)).thenReturn(Optional.of(category));
        // The transitional update must not filter the parent by status.
        when(categoryRepository.findByIdWithParent(9)).thenReturn(Optional.of(inactiveParent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.updateCategory(categoryId, request);

        // Assert
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertSame(inactiveParent, captor.getValue().getParent());
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

        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Shoes", CategoryStatus.ACTIVE, null, newImageId, null, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdWithParent(categoryId)).thenReturn(Optional.of(category));
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
    void deleteCategory_ShouldReject_WhenCategoryStillHasChildren() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Category category = category(categoryId, "Shoes", "shoes", 0);
        category.setUpdatedAt(updatedAt);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
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

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(categoryRepository.countCategoriesInOtherTables(categoryId)).thenReturn(0L);

        // Act
        categoryService.deleteCategory(categoryId, new ModifyExclusiveDTO(updatedAt.plusMinutes(1)));

        // Assert
        assertEquals(MediaStatus.PENDING_DELETE, image.getStatus());
        verify(mediaRepository).save(image);
        verify(categoryRepository).delete(category);
    }
}
