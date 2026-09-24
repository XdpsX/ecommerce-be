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

import com.xdpsx.ecommerce.catalog.category.api.dto.CreateCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.api.dto.UpdateCategoryRequest;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/**
 * Guards the Category Media lifecycle after the CR1 migration. Category had its own lookup path before,
 * so the Brand tests do not protect this behavior.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void createCategory_ShouldActivateTemporaryCategoryImage() {
        // Arrange
        String imageId = "media-id";
        Media media = Media.builder()
                .id(imageId)
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .status(MediaStatus.TEMPORARY)
                .build();
        CreateCategoryRequest request = new CreateCategoryRequest("Shoes", true, imageId, null);

        when(categoryRepository.existsByName("Shoes")).thenReturn(false);
        when(mediaRepository.findAttachableById(imageId, MediaPurpose.CATEGORY_IMAGE))
                .thenReturn(Optional.of(media));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.createCategory(request);

        // Assert
        assertEquals(MediaStatus.ACTIVE, media.getStatus());

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertSame(media, categoryCaptor.getValue().getImage());
    }

    @Test
    void createCategory_ShouldReject_WhenImageIsNotAnAttachableCategoryImage() {
        // Arrange
        String imageId = "media-id";
        CreateCategoryRequest request = new CreateCategoryRequest("Shoes", true, imageId, null);

        when(categoryRepository.existsByName("Shoes")).thenReturn(false);
        // The query enforces TEMPORARY + CATEGORY_IMAGE, so an active or wrong-purpose asset yields empty.
        when(mediaRepository.findAttachableById(imageId, MediaPurpose.CATEGORY_IMAGE))
                .thenReturn(Optional.empty());

        // Act & Assert
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> categoryService.createCategory(request));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        assertEquals("media", exception.getParameters().get("resourceType"));
        assertEquals(imageId, exception.getParameters().get("resourceId"));
        verify(categoryRepository, never()).save(any());
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
        Category category = Category.builder()
                .id(categoryId)
                .name("Shoes")
                .publicFlg(true)
                .image(oldImage)
                .updatedAt(updatedAt)
                .build();

        UpdateCategoryRequest request =
                new UpdateCategoryRequest("Shoes", true, newImageId, null, updatedAt.plusMinutes(1));

        when(categoryRepository.findByIdWithParent(categoryId)).thenReturn(Optional.of(category));
        when(mediaRepository.findAttachableById(newImageId, MediaPurpose.CATEGORY_IMAGE))
                .thenReturn(Optional.of(newImage));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        categoryService.updateCategory(categoryId, request);

        // Assert
        assertEquals(MediaStatus.PENDING_DELETE, oldImage.getStatus());
        assertEquals(MediaStatus.ACTIVE, newImage.getStatus());

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertSame(newImage, categoryCaptor.getValue().getImage());
        verify(mediaRepository, times(2)).save(any(Media.class));
    }

    @Test
    void deleteCategory_ShouldMarkImagePendingDeletion() {
        // Arrange
        int categoryId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        Media image = Media.builder()
                .id("media-id")
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .status(MediaStatus.ACTIVE)
                .build();
        Category category = Category.builder()
                .id(categoryId)
                .name("Shoes")
                .image(image)
                .updatedAt(updatedAt)
                .build();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.countCategoriesInOtherTables(categoryId)).thenReturn(0L);

        // Act
        categoryService.deleteCategory(
                categoryId,
                new com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO(updatedAt.plusMinutes(1)));

        // Assert
        assertEquals(MediaStatus.PENDING_DELETE, image.getStatus());
        verify(mediaRepository).save(image);
        verify(categoryRepository).delete(category);
    }
}
