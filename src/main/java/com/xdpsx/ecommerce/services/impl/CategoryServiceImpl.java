package com.xdpsx.ecommerce.services.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.constants.messages.EMessage;
import com.xdpsx.ecommerce.dtos.category.*;
import com.xdpsx.ecommerce.dtos.common.CheckExistResponse;
import com.xdpsx.ecommerce.dtos.common.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.dtos.common.PageResponse;
import com.xdpsx.ecommerce.entities.Category;
import com.xdpsx.ecommerce.entities.Media;
import com.xdpsx.ecommerce.entities.enums.MediaResourceType;
import com.xdpsx.ecommerce.exceptions.*;
import com.xdpsx.ecommerce.mappers.CategoryMapper;
import com.xdpsx.ecommerce.mappers.PageMapper;
import com.xdpsx.ecommerce.repositories.CategoryRepository;
import com.xdpsx.ecommerce.repositories.MediaRepository;
import com.xdpsx.ecommerce.repositories.specs.CategorySpecification;
import com.xdpsx.ecommerce.services.CategoryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final MediaRepository mediaRepository;

    @Override
    public PageResponse<AdminCategoryResponse> getAdminCategories(AdminCategoryFilter filter) {
        Specification<Category> spec = CategorySpecification.getInstance()
                .buildAdminCategoriesSpec(filter.getName(), filter.getPublicFlg(), filter.getSort(), filter.getLevel());
        Page<Category> categoryPage =
                categoryRepository.findAll(spec, PageRequest.of(filter.getPageNum() - 1, filter.getPageSize()));
        return PageMapper.toPageResponse(categoryPage, CategoryMapper.INSTANCE::toAdminCategoryResponse);
    }

    @Override
    public AdminCategoryResponse getCategory(Integer categoryId) {
        Category category = categoryRepository
                .findPublicByIdWithParent(categoryId)
                .orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, categoryId));
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
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        Category category = CategoryMapper.INSTANCE.toEntity(request);

        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateException(EMessage.DATA_EXISTS, request.name());
        }

        if (request.parentId() != null) {
            Category parent = categoryRepository
                    .findPublicByIdWithParent(request.parentId())
                    .orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, request.parentId()));
            checkCategoryDepth(parent);
            category.setParent(parent);
        }

        if (request.imageId() != null) {
            Media image = mediaRepository
                    .findById(request.imageId())
                    .orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, request.imageId()));
            if (!image.getResourceType().equals(MediaResourceType.CATEGORY)) {
                throw new InvalidResourceTypeException(EMessage.INVALID_RESOURCE_TYPE);
            }
            image.setTempFlg(false);
            category.setImage(image);
        }

        Category savedCategory = categoryRepository.save(category);
        return CategoryMapper.INSTANCE.toResponse(savedCategory);
    }

    @Override
    public CheckExistResponse checkCategoryExist(CategoryExistRequest request) {
        return new CheckExistResponse("name", categoryRepository.existsByName(request.name()));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Integer id, UpdateCategoryRequest request) {
        Category category = categoryRepository
                .findByIdWithParent(id)
                .orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, id));

        if (category.getUpdatedAt() != null && !request.lastRetrievedAt().isAfter(category.getUpdatedAt())) {
            throw new ModifyExclusiveException(EMessage.MODIFY_EXCLUSIVE);
        }

        // Update name
        if (!category.getName().equals(request.name())) {
            if (categoryRepository.existsByName(request.name())) {
                throw new DuplicateException(EMessage.DATA_EXISTS, request.name());
            }
            category.setName(request.name());
        }

        category.setPublicFlg(request.publicFlg());
        // Update image
        updateCategoryImage(category, request.imageId());

        // Update parent
        if (!isSameParent(category, request.parentId())) {
            Category newParent = getParentCategory(request.parentId());
            checkCategoryDepth(newParent);
            category.setParent(newParent);
        }

        Category savedCategory = categoryRepository.save(category);
        return CategoryMapper.INSTANCE.toResponse(savedCategory);
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
            throw new BadRequestException(EMessage.INVALID_DEPTH, Category.MAX_DEPTH);
        }
    }

    private void updateCategoryImage(Category category, String newImageId) {
        Media oldImage = category.getImage();

        if (oldImage == null && newImageId == null) return;

        // If have old image:
        // 1. No new image (newImageId == null) => Delete old image
        // 2. New image != old image => Delete old image
        if (oldImage != null && !oldImage.getId().equals(newImageId)) {
            oldImage.setDeleteFlg(true);
            mediaRepository.save(oldImage);
            category.setImage(null);
        }

        // if have new image and new image != old image => Update image
        if (newImageId != null && (oldImage == null || !oldImage.getId().equals(newImageId))) {
            Media newImage = mediaRepository
                    .findById(newImageId)
                    .orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, newImageId));
            if (!newImage.getResourceType().equals(MediaResourceType.CATEGORY)) {
                throw new InvalidResourceTypeException(EMessage.INVALID_RESOURCE_TYPE);
            }

            newImage.setTempFlg(false);
            mediaRepository.save(newImage);

            category.setImage(newImage);
        }
    }

    private boolean isSameParent(Category category, Integer newParentId) {
        return (category.getParent() == null && newParentId == null)
                || (category.getParent() != null && category.getParent().getId().equals(newParentId));
    }

    private Category getParentCategory(Integer parentId) {
        return (parentId == null)
                ? null
                : categoryRepository
                        .findPublicByIdWithParent(parentId)
                        .orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, parentId));
    }

    @Override
    @Transactional
    public void deleteCategory(Integer id, ModifyExclusiveDTO request) {
        Category category =
                categoryRepository.findById(id).orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, id));
        if (!request.lastRetrievedAt().isAfter(category.getUpdatedAt())) {
            throw new ModifyExclusiveException(EMessage.MODIFY_EXCLUSIVE);
        }
        long countCategories = categoryRepository.countCategoriesInOtherTables(id);
        if (countCategories > 0) {
            throw new InUseException(EMessage.IN_USE);
        }
        if (category.getImage() != null) {
            Media image = category.getImage();
            image.setDeleteFlg(true);
            mediaRepository.save(image);
        }
        categoryRepository.delete(category);
    }
}
