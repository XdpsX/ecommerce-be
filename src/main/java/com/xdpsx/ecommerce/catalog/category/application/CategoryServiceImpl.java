package com.xdpsx.ecommerce.catalog.category.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

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
    private final CategoryHierarchy categoryHierarchy;

    /**
     * Opens one transaction per write attempt. Programmatic instead of
     * {@code @Transactional} because the retry wrapper
     * calls the attempt method on {@code this}, which would bypass the transaction
     * proxy.
     */
    private final TransactionOperations transactionOperations;

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
        // The admin response derives the effective flag from the ancestor chain, so
        // resolve those chains for the
        // whole page before mapping instead of letting each row lazy-load its parents.
        resolveAncestorChains(categoryPage.getContent());
        return PageMapper.toPageResponse(categoryPage, CategoryMapper.INSTANCE::toAdminCategoryResponse);
    }

    /**
     * Loads the requested ancestor chains in at most {@link Category#MAX_DEPTH}
     * batch queries, so the mapping of a
     * page never issues one lazy load per row.
     */
    private void resolveAncestorChains(List<Category> categories) {
        List<Category> frontier = categories;
        for (int hop = 0; hop < Category.MAX_DEPTH; hop++) {
            List<Integer> unresolvedParentIds = frontier.stream()
                    .map(Category::getParent)
                    .filter(parent -> parent != null && !Hibernate.isInitialized(parent))
                    .map(Category::getId)
                    .distinct()
                    .collect(Collectors.toList());
            if (unresolvedParentIds.isEmpty()) {
                return;
            }
            frontier = categoryRepository.findAllByIdInWithAncestry(unresolvedParentIds);
        }
    }

    @Override
    public AdminCategoryResponse getAdminCategory(Integer categoryId) {
        Category category = categoryRepository
                .findByIdWithAncestry(categoryId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", categoryId)));
        return CategoryMapper.INSTANCE.toAdminCategoryResponse(category);
    }

    @Override
    public List<StorefrontCategoryResponse> getStorefrontRootCategories() {
        return visibleCategories().stream()
                .filter(category -> category.getParent() == null)
                .map(CategoryMapper.INSTANCE::toStorefrontCategoryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> visible = visibleCategories();

        // Group the visible nodes by parent so the tree is assembled in memory. A node
        // whose parent was filtered
        // out cannot appear here, because that node is not effectively active either.
        Map<Integer, List<Category>> childrenByParentId = new HashMap<>();
        for (Category category : visible) {
            Integer parentId =
                    category.getParent() == null ? null : category.getParent().getId();
            childrenByParentId
                    .computeIfAbsent(parentId, key -> new ArrayList<>())
                    .add(category);
        }

        return buildTreeLevel(null, childrenByParentId);
    }

    private List<CategoryTreeResponse> buildTreeLevel(
            Integer parentId, Map<Integer, List<Category>> childrenByParentId) {
        // Both the flat query order and this list preserve (displayOrder, id), so every
        // sibling group is stable.
        return childrenByParentId.getOrDefault(parentId, List.of()).stream()
                .map(category -> {
                    CategoryTreeResponse dto = CategoryMapper.INSTANCE.toCategoryTreeResponse(category);
                    dto.setChildren(buildTreeLevel(category.getId(), childrenByParentId));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public StorefrontCategoryResponse getStorefrontCategoryBySlug(String slug) {
        Category category = categoryRepository
                .findBySlugWithAncestry(slug)
                .filter(Category::isEffectivelyActive)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", slug)));
        return CategoryMapper.INSTANCE.toStorefrontCategoryResponse(category);
    }

    /**
     * The storefront read model: one flat query with the image and ancestor chain
     * already fetched, filtered down
     * to the effectively active nodes in memory.
     * {@link Category#isEffectivelyActive()} walks a fetched chain, so
     * no lazy query can be issued here.
     */
    private List<Category> visibleCategories() {
        return categoryRepository.findAllWithAncestryAndImage().stream()
                .filter(Category::isEffectivelyActive)
                .collect(Collectors.toList());
    }

    @Override
    public AdminCategoryResponse createCategory(CreateCategoryRequest request) {
        return categoryHierarchy.executeWithRetry(
                () -> transactionOperations.execute(status -> createCategoryAttempt(request)));
    }

    private AdminCategoryResponse createCategoryAttempt(CreateCategoryRequest request) {
        categoryHierarchy.lockHierarchy();

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
                // An omitted status keeps the previous default of creating a non-visible
                // category.
                .status(request.status() == null ? CategoryStatus.INACTIVE : request.status())
                .build();

        if (request.parentId() != null) {
            // The parent is looked up by ID regardless of status; effective visibility is a
            // storefront concern.
            Category parent = requireCategory(request.parentId());
            category.setParent(parent);
            categoryHierarchy.checkNewNodePlacement(parent);
        }

        // Appending reads the locked sibling group instead of MAX(display_order) + 1,
        // so two concurrent creates
        // cannot take the same position. Roots are a group too.
        List<Category> siblings = categoryHierarchy.lockSiblingGroup(request.parentId());
        categoryHierarchy.normalizeSiblingOrder(siblings);
        category.setDisplayOrder(siblings.size());

        if (request.imageId() != null) {
            category.setImage(activateCategoryImage(request.imageId()));
        }

        Category savedCategory = categoryRepository.save(category);
        return CategoryMapper.INSTANCE.toAdminCategoryResponse(savedCategory);
    }

    @Override
    public AdminCategoryResponse moveCategory(Integer id, MoveCategoryRequest request) {
        return categoryHierarchy.executeWithRetry(
                () -> transactionOperations.execute(status -> moveCategoryAttempt(id, request)));
    }

    private AdminCategoryResponse moveCategoryAttempt(Integer id, MoveCategoryRequest request) {
        categoryHierarchy.lockHierarchy();

        MoveTargets targets = lockForMove(id, request.parentId());
        Category category = targets.moved();
        Category newParent = targets.newParent();

        // Cycle, self-parent and subtree-height checks run before any locked group is
        // renumbered, so a rejected move
        // leaves the hierarchy untouched. The chain lock below is an anchor read, not a
        // mutating call.
        categoryHierarchy.checkMoveAllowed(category, newParent);

        CategoryHierarchy.MoveSnapshot snapshot = categoryHierarchy.lockGroupsForMove(category, request.parentId());

        if (snapshot.isSameGroup()) {
            // Reordering inside one group: the moved node was already removed, so the range
            // is the final group size.
            List<Category> group = snapshot.oldGroup();
            group.add(categoryHierarchy.validateInsertPosition(request.position(), group.size()), category);
            categoryHierarchy.normalizeSiblingOrder(group);
        } else {
            List<Category> oldGroup = snapshot.oldGroup();
            List<Category> newGroup = snapshot.newGroup();
            int position = categoryHierarchy.validateInsertPosition(request.position(), newGroup.size());
            category.setParent(newParent);
            newGroup.add(position, category);
            categoryHierarchy.normalizeSiblingOrder(oldGroup);
            categoryHierarchy.normalizeSiblingOrder(newGroup);
        }

        Category savedCategory = categoryRepository.save(category);
        categoryRepository.flush();
        return CategoryMapper.INSTANCE.toAdminCategoryResponse(savedCategory);
    }

    /**
     * Locks the moved category and the target parent in ascending id order.
     *
     * <p>
     * Two concurrent moves that reference each other (A under B and B under A)
     * would otherwise take the same two
     * row locks in opposite order and deadlock. The trade-off is that when both ids
     * are missing, the parent error is
     * reported instead of the category error.
     */
    private MoveTargets lockForMove(Integer categoryId, Integer parentId) {
        if (parentId != null && parentId < categoryId) {
            Category parent = requireCategoryForUpdate(parentId);
            return new MoveTargets(requireCategoryForUpdate(categoryId), parent);
        }
        Category category = requireCategoryForUpdate(categoryId);
        return new MoveTargets(category, parentId == null ? null : requireCategoryForUpdate(parentId));
    }

    private record MoveTargets(Category moved, Category newParent) {}

    @Override
    public void reorderCategories(ReorderCategoriesRequest request) {
        categoryHierarchy.executeWithRetry(() -> transactionOperations.execute(status -> {
            reorderCategoriesAttempt(request);
            return null;
        }));
    }

    private void reorderCategoriesAttempt(ReorderCategoriesRequest request) {
        categoryHierarchy.lockHierarchy();

        if (request.parentId() != null) {
            requireCategory(request.parentId());
        }

        List<Category> group = categoryHierarchy.lockSiblingGroup(request.parentId());
        List<Integer> requestedIds = request.categoryIds();

        // Partial reorders are rejected: with no unique constraint on (parent_id,
        // display_order) a partial list would
        // leave the remaining nodes on orders the request did not account for.
        if (new HashSet<>(requestedIds).size() != requestedIds.size()
                || new HashSet<>(requestedIds).size() != group.size()) {
            throw new ApplicationException(
                    ErrorCode.INVALID_CATEGORY_ORDER,
                    Map.of("parentId", String.valueOf(request.parentId()), "groupSize", group.size()));
        }

        Map<Integer, Category> byId = new HashMap<>();
        for (Category sibling : group) {
            byId.put(sibling.getId(), sibling);
        }

        for (int index = 0; index < requestedIds.size(); index++) {
            Category sibling = byId.get(requestedIds.get(index));
            if (sibling == null) {
                throw new ApplicationException(
                        ErrorCode.INVALID_CATEGORY_ORDER,
                        Map.of("parentId", String.valueOf(request.parentId()), "categoryId", requestedIds.get(index)));
            }
            sibling.setDisplayOrder(index);
        }

        categoryRepository.flush();
    }

    @Override
    public AdminCategoryResponse updateCategory(Integer id, UpdateCategoryRequest request) {
        return categoryHierarchy.executeWithRetry(
                () -> transactionOperations.execute(status -> updateCategoryAttempt(id, request)));
    }

    private AdminCategoryResponse updateCategoryAttempt(Integer id, UpdateCategoryRequest request) {
        // Locked like every other hierarchy write: Hibernate flushes the whole loaded
        // entity, so an update that read
        // the
        // row before a concurrent move would write the old parent/displayOrder back
        // over it. lastRetrievedAt is a
        // pre-mutation check, not atomic optimistic locking. The hierarchy anchor is
        // what serializes update against
        // move; locking the row as well keeps this operation correct on its own if the
        // anchor strategy ever changes.
        categoryHierarchy.lockHierarchy();
        Category category = categoryRepository
                .findByIdForUpdate(id)
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

        Category savedCategory = categoryRepository.save(category);
        return CategoryMapper.INSTANCE.toAdminCategoryResponse(savedCategory);
    }

    @Override
    public void deleteCategory(Integer id, ModifyExclusiveDTO request) {
        categoryHierarchy.executeWithRetry(() -> transactionOperations.execute(status -> {
            deleteCategoryAttempt(id, request);
            return null;
        }));
    }

    private void deleteCategoryAttempt(Integer id, ModifyExclusiveDTO request) {
        categoryHierarchy.lockHierarchy();

        Category category = categoryRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", id)));
        if (!request.lastRetrievedAt().isAfter(category.getUpdatedAt())) {
            throw new ApplicationException(
                    ErrorCode.CONCURRENT_MODIFICATION, Map.of("resourceType", "category", "resourceId", id));
        }

        // Children are never re-parented or cascaded; the FK is restrictive, so the
        // check keeps this a
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

        // Locking the sibling group keeps a concurrent create or reorder from writing
        // an order that the renumbering
        // below would then silently duplicate.
        Integer parentId =
                category.getParent() == null ? null : category.getParent().getId();
        List<Category> siblings = categoryHierarchy.lockSiblingGroup(parentId);
        siblings.removeIf(sibling -> sibling.getId().equals(category.getId()));

        if (category.getImage() != null) {
            Media image = category.getImage();
            image.markPendingDeletion();
            mediaRepository.save(image);
        }
        categoryRepository.delete(category);

        categoryHierarchy.normalizeSiblingOrder(siblings);
    }

    private Category requireCategory(Integer categoryId) {
        return categoryRepository
                .findByIdWithAncestry(categoryId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", categoryId)));
    }

    /**
     * Resolves a parent inside a hierarchy write. The node is row-locked so a
     * concurrent move cannot reparent it
     * between the validation and the write.
     */
    private Category requireCategoryForUpdate(Integer categoryId) {
        return categoryRepository
                .findByIdForUpdate(categoryId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "category", "resourceId", categoryId)));
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
}
