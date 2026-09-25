package com.xdpsx.ecommerce.catalog.category.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.catalog.category.api.dto.*;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategorySlug;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.category.persistence.CategorySpecification;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.catalog.shared.application.PageMapper;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final MediaRepository mediaRepository;

    @Override
    public PageResponse<AdminCategoryResponse> getAdminCategories(AdminCategoryFilter filter) {
        Specification<Category> spec = CategorySpecification.getInstance()
                .buildAdminCategoriesSpec(
                        filter.getName(),
                        filter.getStatus(),
                        filter.getParentId(),
                        filter.getSort(),
                        filter.getLevel());
        Page<Category> categoryPage =
                categoryRepository.findAll(spec, PageRequest.of(filter.getPageNum() - 1, filter.getPageSize()));
        return PageMapper.toPageResponse(categoryPage, CategoryMapper.INSTANCE::toAdminCategoryResponse);
    }

    @Override
    public AdminCategoryResponse getAdminCategory(Integer categoryId) {
        Category category = categoryRepository
                .findByIdWithParent(categoryId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", categoryId)));
        return CategoryMapper.INSTANCE.toAdminCategoryResponse(category);
    }

    @Override
    public List<CategoryTreeResponse> getCategoryTree(CategoryTreeFilter filter) {
        List<Category> roots = categoryRepository.findAll(
                CategorySpecification.getInstance().buildCategoryTreeSpec(null, filter.sort()));
        return roots.stream()
                .map(c -> buildTree(c, 1, filter.maxLevel(), filter.sort()))
                .collect(Collectors.toList());
    }

    /**
     * Assembles the storefront tree node by node. This still issues one query per node; a bounded read query is
     * deferred to the storefront read model work.
     */
    private CategoryTreeResponse buildTree(Category category, int level, Integer maxLevel, String sort) {
        CategoryTreeResponse dto = CategoryMapper.INSTANCE.toCategoryTreeResponse(category);

        if (maxLevel != null && level >= maxLevel) return dto;

        List<Category> children =
                categoryRepository.findAll(CategorySpecification.getInstance().buildCategoryTreeSpec(category, sort));

        dto.setChildren(children.stream()
                .map(child -> buildTree(child, level + 1, maxLevel, sort))
                .collect(Collectors.toList()));

        return dto;
    }

    @Override
    @Transactional
    public AdminCategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    Map.of("resourceType", "category", "field", "name", "value", request.name()));
        }

        String slug = CategorySlug.normalize(request.name());
        if (slug.isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_CATEGORY_SLUG, Map.of("resourceType", "category"));
        }
        if (categoryRepository.existsBySlug(slug)) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    Map.of("resourceType", "category", "field", "slug", "value", slug));
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(slug)
                // An omitted status keeps the previous default of creating a non-visible category.
                .status(request.status() == null ? CategoryStatus.INACTIVE : request.status())
                .build();

        if (request.parentId() != null) {
            // Transitional: the parent is looked up by ID regardless of status, and only its depth is checked.
            Category parent = requireCategory(request.parentId());
            checkCategoryDepth(parent);
            category.setParent(parent);
        }

        // A new node is appended to the end of its sibling group in the same transaction. Roots are a group too.
        int maxOrder = request.parentId() == null
                ? categoryRepository.findMaxDisplayOrderForRoots()
                : categoryRepository.findMaxDisplayOrderByParentId(request.parentId());
        category.setDisplayOrder(maxOrder + 1);

        if (request.imageId() != null) {
            category.setImage(activateCategoryImage(request.imageId()));
        }

        Category savedCategory = categoryRepository.save(category);
        return CategoryMapper.INSTANCE.toAdminCategoryResponse(savedCategory);
    }

    @Override
    @Transactional
    public AdminCategoryResponse updateCategory(Integer id, UpdateCategoryRequest request) {
        Category category = categoryRepository
                .findByIdWithParent(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", id)));

        if (category.getUpdatedAt() != null && !request.lastRetrievedAt().isAfter(category.getUpdatedAt())) {
            throw new ApplicationException(
                    ErrorCode.CONCURRENT_MODIFICATION, Map.of("resourceType", "category", "resourceId", id));
        }

        // Renaming never changes the slug; only an explicit slug request does.
        if (!category.getName().equals(request.name())) {
            if (categoryRepository.existsByName(request.name())) {
                throw new ApplicationException(
                        ErrorCode.RESOURCE_ALREADY_EXISTS,
                        Map.of("resourceType", "category", "field", "name", "value", request.name()));
            }
            category.setName(request.name());
        }

        if (request.slug() != null && !category.getSlug().equals(request.slug())) {
            if (!CategorySlug.isNormalized(request.slug())) {
                throw new ApplicationException(ErrorCode.INVALID_CATEGORY_SLUG, Map.of("resourceType", "category"));
            }
            if (categoryRepository.existsBySlug(request.slug())) {
                throw new ApplicationException(
                        ErrorCode.RESOURCE_ALREADY_EXISTS,
                        Map.of("resourceType", "category", "field", "slug", "value", request.slug()));
            }
            category.setSlug(request.slug());
        }

        if (request.status() != null) {
            category.setStatus(request.status());
        }

        updateCategoryImage(category, request.imageId());

        // Transitional parent mutation. It still does not reject self-parenting, descendant parents or an
        // over-deep subtree; those invariants belong to the dedicated hierarchy operations.
        if (!isSameParent(category, request.parentId())) {
            Category newParent = request.parentId() == null ? null : requireCategory(request.parentId());
            checkCategoryDepth(newParent);
            category.setParent(newParent);
        }

        Category savedCategory = categoryRepository.save(category);
        return CategoryMapper.INSTANCE.toAdminCategoryResponse(savedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Integer id, ModifyExclusiveDTO request) {
        Category category = categoryRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", id)));
        if (!request.lastRetrievedAt().isAfter(category.getUpdatedAt())) {
            throw new ApplicationException(
                    ErrorCode.CONCURRENT_MODIFICATION, Map.of("resourceType", "category", "resourceId", id));
        }

        // Children are never re-parented or cascaded; the FK is restrictive, so the check keeps this a
        // controlled application error instead of a raw database failure.
        if (categoryRepository.existsByParentId(id)) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_IN_USE, Map.of("resourceType", "category", "resourceId", id));
        }

        long countReferences = categoryRepository.countCategoriesInOtherTables(id);
        if (countReferences > 0) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_IN_USE, Map.of("resourceType", "category", "resourceId", id));
        }

        if (category.getImage() != null) {
            Media image = category.getImage();
            image.markPendingDeletion();
            mediaRepository.save(image);
        }
        categoryRepository.delete(category);
    }

    private Category requireCategory(Integer categoryId) {
        return categoryRepository
                .findByIdWithParent(categoryId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", categoryId)));
    }

    private int getDepth(Category category) {
        int depth = 0;
        while (category != null) {
            depth++;
            category = category.getParent();
        }
        return depth;
    }

    private void checkCategoryDepth(Category category) {
        int depth = getDepth(category);
        if (depth >= Category.MAX_DEPTH) {
            throw new ApplicationException(ErrorCode.INVALID_CATEGORY_DEPTH, Map.of("maxDepth", Category.MAX_DEPTH));
        }
    }

    private Media activateCategoryImage(String imageId) {
        Media image = mediaRepository
                .findAttachableById(imageId, MediaPurpose.CATEGORY_IMAGE)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "media", "resourceId", imageId)));
        image.activate();
        return image;
    }

    private void updateCategoryImage(Category category, String newImageId) {
        Media oldImage = category.getImage();

        if (oldImage == null && newImageId == null) return;

        // If have old image:
        // 1. No new image (newImageId == null) => Delete old image
        // 2. New image != old image => Delete old image
        if (oldImage != null && !oldImage.getId().equals(newImageId)) {
            oldImage.markPendingDeletion();
            mediaRepository.save(oldImage);
            category.setImage(null);
        }

        // if have new image and new image != old image => Update image
        if (newImageId != null && (oldImage == null || !oldImage.getId().equals(newImageId))) {
            Media newImage = activateCategoryImage(newImageId);
            mediaRepository.save(newImage);

            category.setImage(newImage);
        }
    }

    private boolean isSameParent(Category category, Integer newParentId) {
        return (category.getParent() == null && newParentId == null)
                || (category.getParent() != null && category.getParent().getId().equals(newParentId));
    }
}
