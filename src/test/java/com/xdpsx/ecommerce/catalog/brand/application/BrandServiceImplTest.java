package com.xdpsx.ecommerce.catalog.brand.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.api.dto.CreateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.api.dto.DeleteBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.api.dto.StorefrontBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.api.dto.UpdateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private BrandServiceImpl brandService;

    @Test
    void getAdminBrand_ShouldMapLifecycleVersionImageAndCategories() {
        Brand brand = Brand.builder()
                .id(1)
                .name("Adidas")
                .status(BrandStatus.INACTIVE)
                .version(3L)
                .categories(List.of(activeCategory(2)))
                .build();
        when(brandRepository.findDetailById(1)).thenReturn(Optional.of(brand));

        AdminBrandResponse result = brandService.getAdminBrand(1);

        assertEquals(BrandStatus.INACTIVE, result.status());
        assertEquals(3L, result.version());
        assertEquals("Category 2", result.categories().get(0).name());
        verify(brandRepository).findDetailById(1);
    }

    @Test
    void getAdminBrands_ShouldBatchFetchCategoriesOnceForTheResolvedPage() {
        Brand first = Brand.builder()
                .id(1)
                .name("Adidas")
                .status(BrandStatus.ACTIVE)
                .version(0L)
                .build();
        Brand second = Brand.builder()
                .id(2)
                .name("Nike")
                .status(BrandStatus.ACTIVE)
                .version(0L)
                .build();
        when(brandRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 10), 2));

        brandService.getAdminBrands(new com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandFilter());

        verify(brandRepository).fetchCategories(List.of(first, second));
    }

    @Test
    void getStorefrontBrands_ShouldReturnActiveReadModelInStableRepositoryOrder() {
        Brand adidas = Brand.builder()
                .id(2)
                .name("Adidas")
                .status(BrandStatus.ACTIVE)
                .version(4L)
                .image(Media.builder().url("https://example.test/adidas.png").build())
                .categories(List.of(activeCategory(7)))
                .build();
        Brand nike = Brand.builder()
                .id(1)
                .name("Nike")
                .status(BrandStatus.ACTIVE)
                .version(9L)
                .build();
        when(brandRepository.findStorefrontBrands(BrandStatus.ACTIVE)).thenReturn(List.of(adidas, nike));

        List<StorefrontBrandResponse> result = brandService.getStorefrontBrands(null);

        assertEquals(
                List.of("Adidas", "Nike"),
                result.stream().map(StorefrontBrandResponse::name).toList());
        assertEquals("https://example.test/adidas.png", result.get(0).image());
        verify(brandRepository).findStorefrontBrands(BrandStatus.ACTIVE);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void getStorefrontBrands_ShouldRejectHiddenCategoryBeforeBrandQuery() {
        Category hidden = Category.builder()
                .id(9)
                .name("Hidden")
                .slug("hidden")
                .status(CategoryStatus.INACTIVE)
                .build();
        when(categoryRepository.findByIdWithAncestry(9)).thenReturn(Optional.of(hidden));

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> brandService.getStorefrontBrands(9));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        verify(brandRepository, never()).findStorefrontBrandsByCategoryId(anyInt(), any(BrandStatus.class));
    }

    @Test
    void getStorefrontBrands_ShouldFilterDirectlyByVisibleCategory() {
        Category category = activeCategory(7);
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(category));
        when(brandRepository.findStorefrontBrandsByCategoryId(7, BrandStatus.ACTIVE))
                .thenReturn(List.of(Brand.builder()
                        .id(3)
                        .name("Puma")
                        .status(BrandStatus.ACTIVE)
                        .build()));

        List<StorefrontBrandResponse> result = brandService.getStorefrontBrands(7);

        assertEquals(
                List.of("Puma"),
                result.stream().map(StorefrontBrandResponse::name).toList());
        verify(brandRepository).findStorefrontBrandsByCategoryId(7, BrandStatus.ACTIVE);
    }

    @Test
    void createBrand_ShouldSaveStatusAndResolveCategories() {
        Set<Integer> categoryIds = Set.of(1);
        CreateBrandRequest request = new CreateBrandRequest("Puma", BrandStatus.ACTIVE, null, categoryIds);
        when(brandRepository.existsByNameIgnoreCase("Puma")).thenReturn(false);
        when(categoryRepository.findAllByIdInWithAncestry(categoryIds)).thenReturn(List.of(activeCategory(1)));
        when(brandRepository.saveAndFlush(any(Brand.class))).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(7);
            brand.setVersion(0L);
            return brand;
        });

        AdminBrandResponse result = brandService.createBrand(request);

        assertEquals("Puma", result.name());
        assertEquals(BrandStatus.ACTIVE, result.status());
        assertEquals(7, result.id());
        verify(categoryRepository).findAllByIdInWithAncestry(categoryIds);
    }

    @Test
    void createBrand_ShouldTrimNameBeforeCheckingAndSaving() {
        when(brandRepository.existsByNameIgnoreCase("Puma")).thenReturn(false);
        when(brandRepository.saveAndFlush(any(Brand.class))).thenAnswer(invocation -> {
            Brand brand = invocation.getArgument(0);
            brand.setId(8);
            brand.setVersion(0L);
            return brand;
        });

        AdminBrandResponse result =
                brandService.createBrand(new CreateBrandRequest("  Puma  ", BrandStatus.ACTIVE, null, null));

        assertEquals("Puma", result.name());
        verify(brandRepository).existsByNameIgnoreCase("Puma");
    }

    @Test
    void createBrand_ShouldRejectCaseInsensitiveDuplicate() {
        when(brandRepository.existsByNameIgnoreCase("pUmA")).thenReturn(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> brandService.createBrand(new CreateBrandRequest(" pUmA ", BrandStatus.ACTIVE, null, null)));

        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        verify(brandRepository, never()).saveAndFlush(any(Brand.class));
    }

    @Test
    void createBrand_ShouldTranslateDatabaseUniqueConflict() {
        when(brandRepository.existsByNameIgnoreCase("Puma")).thenReturn(false);
        doThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new ConstraintViolationException("Duplicate entry for key 'brands.name'", null, "name")))
                .when(brandRepository)
                .saveAndFlush(any(Brand.class));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> brandService.createBrand(new CreateBrandRequest("Puma", BrandStatus.ACTIVE, null, null)));

        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
    }

    @Test
    void createBrand_ShouldRethrowUnrelatedIntegrityViolation() {
        when(brandRepository.existsByNameIgnoreCase("Puma")).thenReturn(false);
        DataIntegrityViolationException integrityViolation =
                new DataIntegrityViolationException("Cannot add or update a child row for constraint 'fk_brand_media'");
        doThrow(integrityViolation).when(brandRepository).saveAndFlush(any(Brand.class));

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> brandService.createBrand(new CreateBrandRequest("Puma", BrandStatus.ACTIVE, null, null)));

        assertSame(integrityViolation, exception);
    }

    @Test
    void updateBrand_ShouldRethrowUnrelatedIntegrityViolationWhenNameChanges() {
        Brand brand = Brand.builder()
                .id(1)
                .name("Old")
                .status(BrandStatus.ACTIVE)
                .version(2L)
                .build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("New", 1)).thenReturn(false);
        DataIntegrityViolationException integrityViolation =
                new DataIntegrityViolationException("Cannot add or update a child row for constraint 'fk_brand_media'");
        doThrow(integrityViolation).when(brandRepository).saveAndFlush(any(Brand.class));

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> brandService.updateBrand(1, new UpdateBrandRequest("New", BrandStatus.ACTIVE, null, null, 2L)));

        assertSame(integrityViolation, exception);
    }

    @Test
    void updateBrand_ShouldRejectStaleVersionBeforeChangingMediaOrAssociations() {
        Brand brand = Brand.builder()
                .id(1)
                .name("Old")
                .status(BrandStatus.ACTIVE)
                .version(2L)
                .image(Media.builder().id("old-logo").status(MediaStatus.ACTIVE).build())
                .build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        UpdateBrandRequest request = new UpdateBrandRequest("New", BrandStatus.INACTIVE, null, Set.of(), 1L);

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> brandService.updateBrand(1, request));

        assertEquals(ErrorCode.CONCURRENT_MODIFICATION, exception.getCode());
        assertEquals("Old", brand.getName());
        assertEquals(BrandStatus.ACTIVE, brand.getStatus());
        assertEquals(MediaStatus.ACTIVE, brand.getImage().getStatus());
        verifyNoInteractions(mediaRepository, categoryRepository);
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void deleteBrand_ShouldMarkLogoPendingDeleteAndRemoveBrand() {
        Media image = Media.builder().id("logo").status(MediaStatus.ACTIVE).build();
        Brand brand = Brand.builder().id(1).version(4L).image(image).build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));

        brandService.deleteBrand(1, new DeleteBrandRequest(4L));

        assertEquals(MediaStatus.PENDING_DELETE, image.getStatus());
        verify(mediaRepository).save(image);
        verify(brandRepository).delete(brand);
        verify(brandRepository).flush();
    }

    @Test
    void deleteBrand_ShouldRejectBrandReferencedByProductBeforeChangingMedia() {
        Media image = Media.builder().id("logo").status(MediaStatus.ACTIVE).build();
        Brand brand = Brand.builder().id(1).version(4L).image(image).build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(productRepository.existsByBrandId(1)).thenReturn(true);

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> brandService.deleteBrand(1, new DeleteBrandRequest(4L)));

        assertEquals(ErrorCode.RESOURCE_IN_USE, exception.getCode());
        assertEquals(MediaStatus.ACTIVE, image.getStatus());
        verifyNoInteractions(mediaRepository);
        verify(brandRepository, never()).delete(any(Brand.class));
    }

    @Test
    void updateBrand_ShouldLeaveOldLogoWhenReplacementIsNotAttachable() {
        Media oldImage =
                Media.builder().id("old-logo").status(MediaStatus.ACTIVE).build();
        Brand brand = Brand.builder()
                .id(1)
                .name("Old")
                .status(BrandStatus.ACTIVE)
                .version(2L)
                .image(oldImage)
                .build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("New", 1)).thenReturn(false);
        when(mediaRepository.findAttachableById("missing-logo", MediaPurpose.BRAND_LOGO))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> brandService.updateBrand(
                        1, new UpdateBrandRequest("New", BrandStatus.INACTIVE, "missing-logo", null, 2L)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        assertEquals(MediaStatus.ACTIVE, oldImage.getStatus());
        assertEquals("Old", brand.getName());
        assertEquals(BrandStatus.ACTIVE, brand.getStatus());
        verify(mediaRepository, never()).save(any(Media.class));
        verify(brandRepository, never()).saveAndFlush(any(Brand.class));
    }

    @Test
    void updateBrand_ShouldRejectCaseInsensitiveDuplicateWithoutMutation() {
        Brand brand = Brand.builder()
                .id(1)
                .name("Nike")
                .status(BrandStatus.ACTIVE)
                .version(2L)
                .build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("adidas", 1)).thenReturn(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> brandService.updateBrand(
                        1, new UpdateBrandRequest("  adidas ", BrandStatus.INACTIVE, null, null, 2L)));

        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getCode());
        assertEquals("Nike", brand.getName());
        assertEquals(BrandStatus.ACTIVE, brand.getStatus());
        verify(brandRepository, never()).saveAndFlush(any(Brand.class));
    }

    @Test
    void updateBrand_ShouldActivateReplacementAndMarkOldLogoPendingDelete() {
        Media oldImage =
                Media.builder().id("old-logo").status(MediaStatus.ACTIVE).build();
        Media newImage = Media.builder()
                .id("new-logo")
                .purpose(MediaPurpose.BRAND_LOGO)
                .status(MediaStatus.TEMPORARY)
                .build();
        Brand brand = Brand.builder()
                .id(1)
                .name("Nike")
                .status(BrandStatus.ACTIVE)
                .version(2L)
                .image(oldImage)
                .build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(mediaRepository.findAttachableById("new-logo", MediaPurpose.BRAND_LOGO))
                .thenReturn(Optional.of(newImage));
        when(brandRepository.saveAndFlush(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        brandService.updateBrand(1, new UpdateBrandRequest("Nike", BrandStatus.ACTIVE, "new-logo", null, 2L));

        assertEquals(MediaStatus.PENDING_DELETE, oldImage.getStatus());
        assertEquals(MediaStatus.ACTIVE, newImage.getStatus());
        assertSame(newImage, brand.getImage());
        verify(mediaRepository).save(oldImage);
        verify(mediaRepository).save(newImage);
    }

    @Test
    void updateBrand_ShouldClearAssociationsForEmptyCategorySet() {
        Brand brand = Brand.builder()
                .id(1)
                .name("Nike")
                .status(BrandStatus.ACTIVE)
                .version(2L)
                .categories(List.of(activeCategory(1)))
                .build();
        when(brandRepository.findById(1)).thenReturn(Optional.of(brand));
        when(brandRepository.saveAndFlush(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        brandService.updateBrand(1, new UpdateBrandRequest("Nike", BrandStatus.ACTIVE, null, Set.of(), 2L));

        assertTrue(brand.getCategories().isEmpty());
        verify(categoryRepository, never()).findAllByIdInWithAncestry(any());
    }

    @Test
    void createBrand_ShouldRejectWholeAssignmentWhenAnyCategoryIsMissing() {
        Set<Integer> categoryIds = Set.of(1, 2);
        when(brandRepository.existsByNameIgnoreCase("Puma")).thenReturn(false);
        when(categoryRepository.findAllByIdInWithAncestry(categoryIds)).thenReturn(List.of(activeCategory(1)));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> brandService.createBrand(new CreateBrandRequest("Puma", BrandStatus.ACTIVE, null, categoryIds)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    private static Category activeCategory(Integer id) {
        return Category.builder()
                .id(id)
                .name("Category " + id)
                .status(CategoryStatus.ACTIVE)
                .build();
    }
}
