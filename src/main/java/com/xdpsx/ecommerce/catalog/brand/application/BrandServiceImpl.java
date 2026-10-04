package com.xdpsx.ecommerce.catalog.brand.application;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandSpecification;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
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
    private final ProductRepository productRepository;

    public BrandServiceImpl(
            MediaRepository mediaRepository,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository) {
        super(mediaRepository);
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
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
    public List<StorefrontBrandResponse> getStorefrontBrands(Integer categoryId) {
        List<Brand> brands;
        if (categoryId == null) {
            brands = brandRepository.findStorefrontBrands(BrandStatus.ACTIVE);
        } else {
            categoryRepository
                    .findByIdWithAncestry(categoryId)
                    .filter(Category::isEffectivelyActive)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            Map.of("resourceType", "category", "resourceId", categoryId)));
            brands = brandRepository.findStorefrontBrandsByCategoryId(categoryId, BrandStatus.ACTIVE);
        }
        return brands.stream()
                .map(BrandMapper.INSTANCE::toStorefrontBrandResponse)
                .collect(Collectors.toList());
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
        String normalizedName = normalizeName(request.name());
        if (brandRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    Map.of("resourceType", "brand", "field", "name", "value", normalizedName));
        }

        Brand brand = BrandMapper.INSTANCE.toEntity(request);
        brand.setName(normalizedName);

        if (request.categoryIds() != null) {
            List<Category> categories = fetchCategories(request.categoryIds());
            brand.setCategories(categories);
        }
        if (request.imageId() != null) {
            Media image = mediaRepository
                    .findAttachableById(request.imageId(), MediaPurpose.BRAND_LOGO)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            Map.of("resourceType", "media", "resourceId", request.imageId())));
            image.activate();
            brand.setImage(image);
        }

        try {
            Brand savedBrand = brandRepository.saveAndFlush(brand);
            return BrandMapper.INSTANCE.toAdminBrandResponse(savedBrand);
        } catch (DataIntegrityViolationException exception) {
            if (isBrandNameUniqueViolation(exception)) {
                throw duplicateNameException(normalizedName, exception);
            }
            throw exception;
        }
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

        String normalizedName = normalizeName(request.name());

        // Update name
        boolean nameChanged = !brand.getName().equals(normalizedName);
        if (nameChanged) {
            if (brandRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
                throw new ApplicationException(
                        ErrorCode.RESOURCE_ALREADY_EXISTS,
                        Map.of("resourceType", "brand", "field", "name", "value", normalizedName));
            }
        }

        // Update categories
        List<Category> categories = null;
        if (request.categoryIds() != null) {
            categories = fetchCategories(request.categoryIds());
        }

        // Resolve all requested associations before mutating the aggregate. The image helper also validates a
        // replacement before marking the old image for deletion.
        updateImage(brand, request.imageId(), MediaPurpose.BRAND_LOGO);
        if (categories != null) {
            brand.setCategories(categories);
        }
        if (nameChanged) {
            brand.setName(normalizedName);
        }
        brand.setStatus(request.status());
        try {
            Brand savedBrand = brandRepository.saveAndFlush(brand);
            return BrandMapper.INSTANCE.toAdminBrandResponse(savedBrand);
        } catch (DataIntegrityViolationException exception) {
            if (nameChanged && isBrandNameUniqueViolation(exception)) {
                throw duplicateNameException(normalizedName, exception);
            }
            throw exception;
        }
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
        if (productRepository.existsByBrandId(id)) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_IN_USE, Map.of("resourceType", "brand", "resourceId", id));
        }
        if (brand.getImage() != null) {
            Media image = brand.getImage();
            image.markPendingDeletion();
            mediaRepository.save(image);
        }
        try {
            brandRepository.delete(brand);
            brandRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_IN_USE, Map.of("resourceType", "brand", "resourceId", id), exception);
        }
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
        if (categoryIds.stream().anyMatch(Objects::isNull)) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category"));
        }
        Map<Integer, Category> byId = new HashMap<>();
        for (Category category : categoryRepository.findAllByIdInWithAncestry(categoryIds)) {
            byId.put(category.getId(), category);
        }
        return categoryIds.stream()
                .sorted()
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

    private static String normalizeName(String name) {
        return name.trim();
    }

    /**
     * A flush can fail for any foreign key or check constraint touched by the aggregate. Only the unique
     * constraint for {@code brands.name} represents the duplicate-name contract; all other integrity failures
     * must retain their original cause for the transaction boundary to handle them correctly.
     */
    private static boolean isBrandNameUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && isBrandNameConstraint(violation.getConstraintName())) {
                return true;
            }

            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String normalizedMessage = message.toLowerCase(Locale.ROOT);
            boolean duplicate = normalizedMessage.contains("duplicate") || normalizedMessage.contains("unique");
            boolean brandNameKey = normalizedMessage.contains("brands.name")
                    || normalizedMessage.contains("brand_name")
                    || normalizedMessage.contains("for key 'name'")
                    || normalizedMessage.contains("for key `name`");
            if (duplicate && brandNameKey) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBrandNameConstraint(String constraintName) {
        if (constraintName == null) {
            return false;
        }
        String normalized =
                constraintName.toLowerCase(Locale.ROOT).replace("`", "").replace("'", "");
        return normalized.equals("name")
                || normalized.equals("brands.name")
                || (normalized.startsWith("uk") && normalized.contains("brand") && normalized.contains("name"));
    }

    private static ApplicationException duplicateNameException(String name, Throwable cause) {
        return new ApplicationException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                Map.of("resourceType", "brand", "field", "name", "value", name),
                cause);
    }
}
