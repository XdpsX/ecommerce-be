package com.xdpsx.ecommerce.catalog.product.application;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductImage;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantSelection;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductSpecification;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.shared.application.PageMapper;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;
import com.xdpsx.ecommerce.order.persistence.OrderItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private static final int MAX_IMAGES = 5;

    private final ProductMapper productMapper;
    private final PageMapper pageMapper;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductSpecification spec;
    private final ProductVariantRepository productVariantRepository;
    private final VariantOptionValueRepository variantOptionValueRepository;
    private final MediaRepository mediaRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProductResponse> filterAllProducts(ProductParams params) {
        Map<Long, List<Long>> optionValueIdsByOption = validateOptionValueIds(params.getOptionValueIds());
        Specification<Product> productSpec = optionValueIdsByOption.isEmpty()
                ? spec.getFiltersSpec(
                        params.getSearch(),
                        params.getSort(),
                        params.getHasPublished(),
                        params.getMinPrice(),
                        params.getMaxPrice(),
                        params.getHasDiscount(),
                        params.getInStock(),
                        params.getCategoryId(),
                        params.getBrandId())
                : spec.getFiltersSpec(
                        params.getSearch(),
                        params.getSort(),
                        params.getHasPublished(),
                        params.getMinPrice(),
                        params.getMaxPrice(),
                        params.getHasDiscount(),
                        params.getInStock(),
                        params.getCategoryId(),
                        params.getBrandId(),
                        optionValueIdsByOption);
        Page<Product> page =
                productRepository.findAll(productSpec, PageRequest.of(params.getPageNum() - 1, params.getPageSize()));
        loadImages(page.getContent());
        return pageMapper.toProductPageResponse(page);
    }

    @Transactional(readOnly = true)
    @Override
    public ProductDetailsDTO getProductById(Long id) {
        Product product = productRepository.findProductById(id).orElseThrow(() -> notFound("product", id));
        return withVariantSelection(productMapper.fromEntityToDetailsDTO(product), id);
    }

    @Transactional(readOnly = true)
    @Override
    public ProductDetailsDTO getProductBySlug(String slug) {
        Product product = productRepository.findProductBySlug(slug).orElseThrow(() -> notFound("product", slug));
        return withVariantSelection(productMapper.fromEntityToDetailsDTO(product), product.getId());
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductOptionResponse> getFilterOptions(Integer categoryId, Integer brandId) {
        return toOptions(productVariantRepository.findActiveFilterOptionValues(categoryId, brandId));
    }

    @Transactional
    @Override
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (request.isPublished()) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "published", "reason", "create variants before publishing the product"));
        }
        if (productRepository.existsBySlug(request.getSlug())) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    Map.of("resourceType", "product", "field", "slug", "value", request.getSlug()));
        }
        Category category = requireEffectivelyActiveCategory(request.getCategoryId());
        Brand brand = requireActiveBrand(request.getBrandId());
        List<Media> media = resolveNewMedia(request.getImageIds());
        Product product = productMapper.fromCreateRequestToEntity(request);
        product.setCategory(category);
        product.setBrand(brand);
        replaceImages(product, media);
        media.forEach(Media::activate);
        return productMapper.fromEntityToResponse(productRepository.save(product));
    }

    @Transactional
    @Override
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("product", id));
        List<Media> replacementMedia =
                request.getImageIds() == null ? null : resolveReplacementMedia(product, request.getImageIds());
        Category targetCategory = product.getCategory();
        if (request.getCategoryId() != null && !Objects.equals(targetCategory.getId(), request.getCategoryId())) {
            targetCategory = requireEffectivelyActiveCategory(request.getCategoryId());
        }
        Brand targetBrand = product.getBrand();
        if (request.getBrandId() != null && !Objects.equals(targetBrand.getId(), request.getBrandId())) {
            targetBrand = requireActiveBrand(request.getBrandId());
        }
        if (request.isPublished()) {
            validatePublishEligibility(targetCategory, targetBrand);
            requireActiveVariant(id);
        }
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDiscountPercent(request.getDiscountPercent());
        product.setInStock(request.isInStock());
        product.setPublished(request.isPublished());
        product.setDescription(request.getDescription());
        if (!Objects.equals(request.getSlug(), product.getSlug())) {
            if (productRepository.existsBySlug(request.getSlug())) {
                throw new ApplicationException(
                        ErrorCode.RESOURCE_ALREADY_EXISTS,
                        Map.of("resourceType", "product", "field", "slug", "value", request.getSlug()));
            }
            product.setSlug(request.getSlug());
        }
        product.setCategory(targetCategory);
        product.setBrand(targetBrand);
        if (replacementMedia != null) {
            product.getImages().stream()
                    .map(ProductImage::getMedia)
                    .filter(item -> replacementMedia.stream()
                            .noneMatch(retained -> retained.getId().equals(item.getId())))
                    .forEach(Media::markPendingDeletion);
            product.getImages().clear();
            productRepository.flush();
            appendImages(product, replacementMedia);
            replacementMedia.stream()
                    .filter(item -> item.getStatus() == MediaStatus.TEMPORARY)
                    .forEach(Media::activate);
        }
        return productMapper.fromEntityToResponse(productRepository.save(product));
    }

    @Transactional
    @Override
    public void deleteProduct(Long id) {
        Product product = productRepository.findProductById(id).orElseThrow(() -> notFound("product", id));
        if (orderItemRepository.existsByProductId(id)) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_IN_USE, Map.of("resourceType", "product", "resourceId", id));
        }
        product.getImages().stream().map(ProductImage::getMedia).forEach(Media::markPendingDeletion);
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_IN_USE, Map.of("resourceType", "product", "resourceId", id), exception);
        }
    }

    @Transactional
    @Override
    public void publishProduct(Long id, boolean status) {
        Product product = productRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("product", id));
        if (status) {
            validatePublishEligibility(product.getCategory(), product.getBrand());
            requireActiveVariant(id);
        }
        product.setPublished(status);
        productRepository.save(product);
    }

    @Override
    public Map<String, Boolean> checkExistsProduct(String slug) {
        return Map.of("slugExists", productRepository.existsBySlug(slug));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProductResponse> getDiscountProducts(int pageNum, int pageSize) {
        Page<Product> page = productRepository.findAll(
                spec.hasDiscount(true).and(spec.hasPublished(true)), PageRequest.of(pageNum - 1, pageSize));
        loadImages(page.getContent());
        return pageMapper.toProductPageResponse(page);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProductResponse> getLatestProducts(int pageNum, int pageSize) {
        Page<Product> page = productRepository.findAll(
                spec.getSortSpec("-date").and(spec.hasPublished(true)), PageRequest.of(pageNum - 1, pageSize));
        loadImages(page.getContent());
        return pageMapper.toProductPageResponse(page);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProductResponse> getProductsByCategoryId(
            Integer categoryId,
            int pageNum,
            int pageSize,
            List<Integer> brandIds,
            String sort,
            Double minPrice,
            Double maxPrice) {
        categoryRepository.findById(categoryId).orElseThrow(() -> notFound("category", categoryId));
        Specification<Product> productSpec = spec.belongsToCategory(categoryId)
                .and(spec.belongsToBrands(brandIds))
                .and(spec.hasPublished(true))
                .and(spec.getSortSpec(sort))
                .and(spec.hasMinPrice(minPrice))
                .and(spec.hasMaxPrice(maxPrice));
        Page<Product> page = productRepository.findAll(productSpec, PageRequest.of(pageNum - 1, pageSize));
        loadImages(page.getContent());
        return pageMapper.toProductPageResponse(page);
    }

    private List<Media> resolveNewMedia(List<String> imageIds) {
        List<String> ids = validateImageIds(imageIds);
        if (ids.isEmpty()) return List.of();
        List<Media> media = resolveByIds(ids);
        if (media.size() != ids.size()) throw notFound("media", ids.get(0));
        Map<String, Media> byId = media.stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        return ids.stream()
                .map(id -> {
                    Media item = byId.get(id);
                    if (item.getPurpose() != MediaPurpose.PRODUCT_IMAGE || item.getStatus() != MediaStatus.TEMPORARY) {
                        throw notFound("media", id);
                    }
                    return item;
                })
                .toList();
    }

    private List<Media> resolveReplacementMedia(Product product, List<String> imageIds) {
        List<String> ids = validateImageIds(imageIds);
        var current = product.getImages().stream()
                .map(ProductImage::getMedia)
                .collect(Collectors.toMap(Media::getId, Function.identity()));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Media> media = resolveByIds(ids);
        Map<String, Media> byId = media.stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        List<Media> ordered = ids.stream()
                .map(id -> {
                    Media item = byId.get(id);
                    if (item == null
                            || item.getPurpose() != MediaPurpose.PRODUCT_IMAGE
                            || (item.getStatus() != MediaStatus.TEMPORARY
                                    && (!current.containsKey(id) || item.getStatus() != MediaStatus.ACTIVE))) {
                        throw notFound("media", id);
                    }
                    return item;
                })
                .toList();
        return ordered;
    }

    private List<Media> resolveByIds(List<String> ids) {
        List<Media> loaded = mediaRepository.findAllByIdInForUpdate(ids);
        if (loaded == null) loaded = List.of();
        Map<String, Media> byId = loaded.stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        return ids.stream().map(id -> byId.get(id)).filter(Objects::nonNull).toList();
    }

    private List<String> validateImageIds(List<String> imageIds) {
        if (imageIds == null) return List.of();
        if (imageIds.size() > MAX_IMAGES
                || imageIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(imageIds).size() != imageIds.size()) {
            throw new ApplicationException(ErrorCode.INVALID_PRODUCT_IMAGE_COUNT, Map.of("maxImages", MAX_IMAGES));
        }
        return imageIds;
    }

    private void replaceImages(Product product, List<Media> media) {
        product.getImages().clear();
        appendImages(product, media);
    }

    private void appendImages(Product product, List<Media> media) {
        for (int index = 0; index < media.size(); index++) {
            product.getImages()
                    .add(ProductImage.builder()
                            .product(product)
                            .media(media.get(index))
                            .displayOrder(index)
                            .build());
        }
    }

    private Brand requireActiveBrand(Integer brandId) {
        Brand brand = brandRepository.findById(brandId).orElseThrow(() -> notFound("brand", brandId));
        if (brand.getStatus() != BrandStatus.ACTIVE) throw notFound("brand", brandId);
        return brand;
    }

    private void validatePublishEligibility(Category category, Brand brand) {
        requireEffectivelyActiveCategory(category.getId());
        requireActiveBrand(brand.getId());
    }

    private void requireActiveVariant(Long productId) {
        if (!productVariantRepository.existsActiveByProductId(productId)) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "published", "reason", "product must have at least one active variant"));
        }
    }

    private Category requireEffectivelyActiveCategory(Integer categoryId) {
        return categoryRepository
                .findByIdWithAncestry(categoryId)
                .filter(Category::isEffectivelyActive)
                .orElseThrow(() -> notFound("category", categoryId));
    }

    private void loadImages(List<Product> products) {
        if (!products.isEmpty())
            productRepository.findAllWithImagesByIdIn(
                    products.stream().map(Product::getId).toList());
    }

    private ProductDetailsDTO withVariantSelection(ProductDetailsDTO details, Long productId) {
        List<ProductVariant> variants =
                productVariantRepository.findActiveWithSelectionsAndOptionsByProductId(productId);
        Map<Long, ProductOptionAccumulator> options = new LinkedHashMap<>();
        List<ProductVariantSelectionResponse> variantResponses = variants.stream()
                .map(variant -> {
                    if (variant.getSelections().stream().anyMatch(selection -> !isActiveSelection(selection))) {
                        return null;
                    }
                    List<ProductVariantSelection> selections = variant.getSelections().stream()
                            .sorted(Comparator.comparingInt((ProductVariantSelection selection) -> selection
                                            .getOptionValue()
                                            .getOption()
                                            .getDisplayOrder())
                                    .thenComparing(selection ->
                                            selection.getOptionValue().getId()))
                            .toList();
                    List<Long> valueIds = selections.stream()
                            .map(ProductVariantSelection::getOptionValueId)
                            .toList();
                    selections.forEach(selection -> {
                        VariantOptionValue value = selection.getOptionValue();
                        if (value == null
                                || value.getOption() == null
                                || value.getStatus() != VariantOptionStatus.ACTIVE
                                || value.getOption().getStatus() != VariantOptionStatus.ACTIVE) return;
                        options.computeIfAbsent(
                                        value.getOption().getId(),
                                        ignored -> new ProductOptionAccumulator(
                                                value.getOption().getId(),
                                                value.getOption().getCode(),
                                                value.getOption().getName(),
                                                value.getOption().getDisplayOrder()))
                                .values
                                .putIfAbsent(
                                        value.getId(),
                                        new ProductOptionValueResponse(
                                                value.getId(),
                                                value.getCode(),
                                                value.getName(),
                                                value.getDisplayOrder()));
                    });
                    return new ProductVariantSelectionResponse(variant.getId(), variant.getSku(), valueIds);
                })
                .filter(Objects::nonNull)
                .toList();
        details.setOptions(options.values().stream()
                .sorted(Comparator.comparingInt((ProductOptionAccumulator option) -> option.displayOrder)
                        .thenComparing(option -> option.id))
                .map(ProductOptionAccumulator::toResponse)
                .toList());
        details.setVariants(variantResponses);
        return details;
    }

    private List<ProductOptionResponse> toOptions(List<ProductVariantRepository.FilterOptionValueView> views) {
        Map<Long, ProductOptionAccumulator> options = new LinkedHashMap<>();
        for (ProductVariantRepository.FilterOptionValueView view : views) {
            options.computeIfAbsent(
                            view.getOptionId(),
                            ignored -> new ProductOptionAccumulator(
                                    view.getOptionId(),
                                    view.getOptionCode(),
                                    view.getOptionName(),
                                    view.getOptionDisplayOrder()))
                    .values
                    .putIfAbsent(
                            view.getValueId(),
                            new ProductOptionValueResponse(
                                    view.getValueId(),
                                    view.getValueCode(),
                                    view.getValueName(),
                                    view.getValueDisplayOrder()));
        }
        return options.values().stream()
                .sorted(Comparator.comparingInt((ProductOptionAccumulator option) -> option.displayOrder)
                        .thenComparing(option -> option.id))
                .map(ProductOptionAccumulator::toResponse)
                .toList();
    }

    private static boolean isActiveSelection(ProductVariantSelection selection) {
        VariantOptionValue value = selection.getOptionValue();
        return value != null
                && value.getOption() != null
                && value.getStatus() == VariantOptionStatus.ACTIVE
                && value.getOption().getStatus() == VariantOptionStatus.ACTIVE;
    }

    private Map<Long, List<Long>> validateOptionValueIds(List<Long> optionValueIds) {
        if (optionValueIds == null || optionValueIds.isEmpty()) return Map.of();
        if (optionValueIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(optionValueIds).size() != optionValueIds.size()) {
            throw invalidOptionValues("must not contain duplicate values");
        }
        List<VariantOptionValue> values = variantOptionValueRepository.findAllByIdInWithOption(optionValueIds);
        if (values.size() != optionValueIds.size()
                || values.stream()
                        .anyMatch(value -> value.getStatus() != VariantOptionStatus.ACTIVE
                                || value.getOption() == null
                                || value.getOption().getStatus() != VariantOptionStatus.ACTIVE)) {
            throw invalidOptionValues("every option value must exist and be active");
        }
        return values.stream()
                .collect(Collectors.groupingBy(
                        value -> value.getOption().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(VariantOptionValue::getId, Collectors.toList())));
    }

    private static ApplicationException invalidOptionValues(String reason) {
        return new ApplicationException(
                ErrorCode.VALIDATION_FAILED, Map.of("field", "optionValueIds", "reason", reason));
    }

    private static final class ProductOptionAccumulator {
        private final Long id;
        private final String code;
        private final String name;
        private final Integer displayOrder;
        private final Map<Long, ProductOptionValueResponse> values = new LinkedHashMap<>();

        private ProductOptionAccumulator(Long id, String code, String name, Integer displayOrder) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.displayOrder = displayOrder;
        }

        private ProductOptionResponse toResponse() {
            return new ProductOptionResponse(
                    id,
                    code,
                    name,
                    displayOrder,
                    values.values().stream()
                            .sorted(Comparator.comparingInt((ProductOptionValueResponse value) -> value.displayOrder())
                                    .thenComparing(value -> value.id()))
                            .toList());
        }
    }

    private static ApplicationException notFound(String type, Object id) {
        return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", type, "resourceId", id));
    }
}
