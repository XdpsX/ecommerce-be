package com.xdpsx.ecommerce.catalog.brand.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.api.dto.CreateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.api.dto.DeleteBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.api.dto.UpdateBrandRequest;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
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
    void createBrand_ShouldSaveStatusAndResolveCategories() {
        Set<Integer> categoryIds = Set.of(1);
        CreateBrandRequest request = new CreateBrandRequest("Puma", BrandStatus.ACTIVE, null, categoryIds);
        when(brandRepository.existsByName("Puma")).thenReturn(false);
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
    }

    @Test
    void createBrand_ShouldRejectWholeAssignmentWhenAnyCategoryIsMissing() {
        Set<Integer> categoryIds = Set.of(1, 2);
        when(brandRepository.existsByName("Puma")).thenReturn(false);
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
