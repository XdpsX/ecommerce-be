package com.xdpsx.ecommerce.catalog.brand.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandDetailResponse;
import com.xdpsx.ecommerce.catalog.brand.api.dto.BrandExistRequest;
import com.xdpsx.ecommerce.catalog.brand.api.dto.CreateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.api.dto.UpdateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.error.DuplicateException;
import com.xdpsx.ecommerce.common.error.EMessage;
import com.xdpsx.ecommerce.common.error.ModifyExclusiveException;
import com.xdpsx.ecommerce.common.error.NotFoundException;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private BrandServiceImpl brandService;

    @Test
    void testCheckBrandExist_shouldReturnTrueWhenNameExists() {
        // Arrange
        String brandName = "Nike";
        BrandExistRequest request = new BrandExistRequest(brandName);
        when(brandRepository.existsByName(brandName)).thenReturn(true);

        // Act
        CheckExistResponse response = brandService.checkBrandExist(request);

        // Assert
        assertEquals("name", response.field());
        assertTrue(response.exists());
        verify(brandRepository).existsByName(brandName);
    }

    @Test
    void testGetAdminBrandDetail_shouldReturnBrandDetail() {
        // Arrange
        int brandId = 1;
        Brand brand = Brand.builder().id(brandId).name("Adidas").build();
        when(brandRepository.findDetailById(brandId)).thenReturn(Optional.of(brand));

        // Act
        BrandDetailResponse response = brandService.getAdminBrandDetail(brandId);

        // Assert
        assertEquals("Adidas", response.name());
        verify(brandRepository).findDetailById(brandId);
    }

    @Test
    void testCreateBrand_shouldSaveBrandWithImageAndCategories() {
        // Arrange
        String imageId = "img123";
        Set<Integer> categoryIds = Set.of(1, 2);
        Media media = Media.builder().id(imageId).tempFlg(true).build();

        CreateBrandRequest request = new CreateBrandRequest("Puma", true, imageId, categoryIds);
        when(brandRepository.existsByName("Puma")).thenReturn(false);
        when(mediaRepository.findPublicTempMediaById(imageId, MediaResourceType.BRAND))
                .thenReturn(Optional.of(media));
        when(categoryRepository.findPublicById(anyInt())).thenReturn(Optional.of(new Category()));
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BrandDetailResponse response = brandService.createBrand(request);

        // Assert
        assertEquals("Puma", response.name());
        assertFalse(media.isTempFlg());
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    void testUpdateBrand_shouldUpdateImageAndCategories() {
        // Arrange
        int brandId = 1;
        String oldImageId = "imgOld";
        String newImageId = "imgNew";
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);

        Brand brand = Brand.builder()
                .id(brandId)
                .name("OldName")
                .publicFlg(false)
                .image(Media.builder().id(oldImageId).build())
                .updatedAt(updatedAt)
                .build();

        UpdateBrandRequest request =
                new UpdateBrandRequest("NewName", true, newImageId, Set.of(1), updatedAt.plusMinutes(1));

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(brandRepository.existsByName(request.name())).thenReturn(false);
        when(mediaRepository.findPublicTempMediaById(newImageId, MediaResourceType.BRAND))
                .thenReturn(
                        Optional.of(Media.builder().id(newImageId).tempFlg(true).build()));
        when(categoryRepository.findPublicById(1)).thenReturn(Optional.of(new Category()));
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BrandDetailResponse response = brandService.updateBrand(brandId, request);

        // Assert
        assertEquals("NewName", response.name());
        verify(mediaRepository, times(2)).save(any(Media.class));
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    void testDeleteBrand_shouldMarkImageDeletedAndDeleteBrand() {
        // Arrange
        int brandId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        ModifyExclusiveDTO request = new ModifyExclusiveDTO(updatedAt.plusMinutes(1));

        Media image = Media.builder().id("img123").deleteFlg(false).build();
        Brand brand =
                Brand.builder().id(brandId).updatedAt(updatedAt).image(image).build();

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

        // Act
        brandService.deleteBrand(brandId, request);

        // Assert
        assertTrue(image.isDeleteFlg());
        verify(mediaRepository).save(image);
        verify(brandRepository).delete(brand);
    }

    @Test
    void testGetAdminBrandDetail_shouldThrowNotFoundExceptionWhenBrandDoesNotExist() {
        // Arrange
        int brandId = 1;
        when(brandRepository.findDetailById(brandId)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception =
                assertThrows(NotFoundException.class, () -> brandService.getAdminBrandDetail(brandId));
        assertEquals(EMessage.NOT_FOUND.message(), exception.getMessage());
        verify(brandRepository).findDetailById(brandId);
    }

    @Test
    void testCreateBrand_shouldThrowDuplicateExceptionWhenBrandNameExists() {
        // Arrange
        CreateBrandRequest request = new CreateBrandRequest("Puma", true, null, null);
        when(brandRepository.existsByName("Puma")).thenReturn(true);

        // Act & Assert
        DuplicateException exception = assertThrows(DuplicateException.class, () -> brandService.createBrand(request));
        assertEquals(EMessage.DATA_EXISTS.message(), exception.getMessage());
        verify(brandRepository).existsByName("Puma");
    }

    @Test
    void testUpdateBrand_shouldThrowModifyExclusiveExceptionWhenLastRetrievedAtIsInvalid() {
        // Arrange
        int brandId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        UpdateBrandRequest request = new UpdateBrandRequest("NewName", true, null, null, updatedAt.minusMinutes(1));

        Brand brand = Brand.builder().id(brandId).updatedAt(updatedAt).build();
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

        // Act & Assert
        ModifyExclusiveException exception =
                assertThrows(ModifyExclusiveException.class, () -> brandService.updateBrand(brandId, request));
        assertEquals(EMessage.MODIFY_EXCLUSIVE.message(), exception.getMessage());
        verify(brandRepository).findById(brandId);
    }

    @Test
    void testDeleteBrand_shouldThrowModifyExclusiveExceptionWhenLastRetrievedAtIsInvalid() {
        // Arrange
        int brandId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        ModifyExclusiveDTO request = new ModifyExclusiveDTO(updatedAt.minusMinutes(1));

        Brand brand = Brand.builder().id(brandId).updatedAt(updatedAt).build();
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

        // Act & Assert
        ModifyExclusiveException exception =
                assertThrows(ModifyExclusiveException.class, () -> brandService.deleteBrand(brandId, request));
        assertEquals(EMessage.MODIFY_EXCLUSIVE.message(), exception.getMessage());
        verify(brandRepository).findById(brandId);
    }

    @Test
    void testDeleteBrand_shouldThrowNotFoundExceptionWhenMediaDoesNotExist() {
        // Arrange
        int brandId = 1;
        LocalDateTime updatedAt = LocalDateTime.now();
        ModifyExclusiveDTO request = new ModifyExclusiveDTO(updatedAt.minusMinutes(1));

        when(brandRepository.findById(brandId)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception =
                assertThrows(NotFoundException.class, () -> brandService.deleteBrand(brandId, request));
        assertEquals(EMessage.NOT_FOUND.message(), exception.getMessage());
        verify(brandRepository).findById(brandId);
    }

    @Test
    void testDeleteBrand_shouldNotMarkImageDeletedWhenBrandDoesNotHaveImage() {
        // Arrange
        int brandId = 1;
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
        ModifyExclusiveDTO request = new ModifyExclusiveDTO(updatedAt.plusMinutes(1));

        Brand brand =
                Brand.builder().id(brandId).updatedAt(updatedAt).image(null).build();

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

        // Act
        brandService.deleteBrand(brandId, request);

        // Assert
        verify(mediaRepository, never()).save(any(Media.class));
        verify(brandRepository).delete(brand);
    }

    @Test
    void testFetchCategories_shouldThrowNotFoundExceptionWhenCategoryDoesNotExist() {
        // Arrange
        Set<Integer> categoryIds = Set.of(1, 2);
        when(categoryRepository.findPublicById(anyInt())).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> brandService.createBrand(new CreateBrandRequest("Puma", true, null, categoryIds)));
        assertEquals(EMessage.NOT_FOUND.message(), exception.getMessage());
        verify(categoryRepository).findPublicById(anyInt());
    }
}
