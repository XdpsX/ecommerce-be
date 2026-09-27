package com.xdpsx.ecommerce.catalog.brand.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandSpecification;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.shared.application.PageMapper;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@Service
public class BrandServiceImpl extends AbstractImageUpdatableService implements BrandService {
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    public BrandServiceImpl(
            MediaRepository mediaRepository, BrandRepository brandRepository, CategoryRepository categoryRepository) {
        super(mediaRepository);
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Two-step admin read: page the filtered/sorted Brands first (image fetched, collection deliberately not
     * fetched so the page and count queries stay exact), then batch-fetch the categories for just that page.
     * Total bounded queries: page + count + one detail batch, independent of page size.
     */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<AdminBrandResponse> getAdminBrands(AdminBrandFilter filter) {
        Specification<Brand> spec = BrandSpecification.getInstance()
                .buildAdminBrandsSpec(filter.getName(), filter.getStatus(), filter.getSort());
        Page<Brand> brandPage =
                brandRepository.findAll(spec, PageRequest.of(filter.getPageNum() - 1, filter.getPageSize()));

        List<Brand> content = brandPage.getContent();
        if (!content.isEmpty()) {
            // Initializes the collection on the same managed entities; page order is preserved because the
            // batch fetch only populates the already-loaded instances in the persistence context.
            brandRepository.fetchCategories(content);
        }

        return PageMapper.toPageResponse(brandPage, BrandMapper.INSTANCE::toAdminBrandResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public AdminBrandResponse getAdminBrand(Integer id) {
        Brand brand = brandRepository
                .findDetailById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "brand", "resourceId", id)));
        return BrandMapper.INSTANCE.toAdminBrandResponse(brand);
    }

    @Transactional
    @Override
    public AdminBrandResponse createBrand(CreateBrandRequest request) {
        if (brandRepository.existsByName(request.name())) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    Map.of("resourceType", "brand", "field", "name", "value", request.name()));
        }

        Brand brand = BrandMapper.INSTANCE.toEntity(request);

        if (request.imageId() != null) {
            Media image = mediaRepository
                    .findAttachableById(request.imageId(), MediaPurpose.BRAND_LOGO)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            Map.of("resourceType", "media", "resourceId", request.imageId())));
            image.activate();
            brand.setImage(image);
        }
        if (request.categoryIds() != null) {
            List<Category> categories = fetchCategories(request.categoryIds());
            brand.setCategories(categories);
        }

        Brand savedBrand = brandRepository.saveAndFlush(brand);
        return BrandMapper.INSTANCE.toAdminBrandResponse(savedBrand);
    }

    @Transactional
    @Override
    public AdminBrandResponse updateBrand(Integer id, UpdateBrandRequest request) {
        Brand brand = brandRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "brand", "resourceId", id)));

        // Optimistic-concurrency pre-check on the client-visible version. JPA @Version still guards the write
        // itself; this check turns a stale token into a controlled 409 before any state is mutated.
        if (!Objects.equals(brand.getVersion(), request.version())) {
            throw new ApplicationException(
                    ErrorCode.CONCURRENT_MODIFICATION, Map.of("resourceType", "brand", "resourceId", id));
        }

        // Update name
        if (!brand.getName().equals(request.name())) {
            if (brandRepository.existsByName(request.name())) {
                throw new ApplicationException(
                        ErrorCode.RESOURCE_ALREADY_EXISTS,
                        Map.of("resourceType", "brand", "field", "name", "value", request.name()));
            }
            brand.setName(request.name());
        }

        brand.setStatus(request.status());

        // Update image
        updateImage(brand, request.imageId(), MediaPurpose.BRAND_LOGO);

        // Update categories
        if (request.categoryIds() != null) {
            List<Category> categories = fetchCategories(request.categoryIds());
            brand.setCategories(categories);
        }
        Brand savedBrand = brandRepository.saveAndFlush(brand);
        return BrandMapper.INSTANCE.toAdminBrandResponse(savedBrand);
    }

    @Transactional
    @Override
    public void deleteBrand(Integer id, DeleteBrandRequest request) {
        Brand brand = brandRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "brand", "resourceId", id)));
        if (!Objects.equals(brand.getVersion(), request.version())) {
            throw new ApplicationException(
                    ErrorCode.CONCURRENT_MODIFICATION, Map.of("resourceType", "brand", "resourceId", id));
        }
        if (brand.getImage() != null) {
            Media image = brand.getImage();
            image.markPendingDeletion();
            mediaRepository.save(image);
        }
        brandRepository.delete(brand);
    }

    /**
     * Resolves the requested set in one query and rejects the whole operation when
     * any category is missing or not
     * effectively active (itself and every ancestor stored {@code ACTIVE}), so no
     * partial brand assignment is saved.
     */
    private List<Category> fetchCategories(Set<Integer> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        Map<Integer, Category> byId = new HashMap<>();
        for (Category category : categoryRepository.findAllByIdInWithAncestry(categoryIds)) {
            byId.put(category.getId(), category);
        }
        return categoryIds.stream()
                .map(categoryId -> {
                    Category category = byId.get(categoryId);
                    if (category == null || !category.isEffectivelyActive()) {
                        throw new ApplicationException(
                                ErrorCode.RESOURCE_NOT_FOUND,
                                Map.of("resourceType", "category", "resourceId", categoryId));
                    }
                    return category;
                })
                .collect(Collectors.toList());
    }
}
