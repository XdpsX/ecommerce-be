package com.xdpsx.ecommerce.catalog.brand.application;

import java.util.List;
import java.util.Map;
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
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.catalog.shared.application.PageMapper;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;
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

    @Override
    public PageResponse<AdminBrandResponse> getAdminBrands(AdminBrandFilter filter) {
        Specification<Brand> spec = BrandSpecification.getInstance()
                .buildAdminBrandsSpec(filter.getName(), filter.getPublicFlg(), filter.getSort());
        Page<Brand> brandPage =
                brandRepository.findAll(spec, PageRequest.of(filter.getPageNum() - 1, filter.getPageSize()));
        return PageMapper.toPageResponse(brandPage, BrandMapper.INSTANCE::toAdminBrandResponse);
    }

    @Override
    public BrandDetailResponse getAdminBrandDetail(Integer id) {
        Brand brand = brandRepository
                .findDetailById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "brand", "resourceId", id)));
        return BrandMapper.INSTANCE.toBrandDetailResponse(brand);
    }

    @Transactional
    @Override
    public BrandDetailResponse createBrand(CreateBrandRequest request) {
        Brand brand = BrandMapper.INSTANCE.toEntity(request);
        if (brandRepository.existsByName(request.name())) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    Map.of("resourceType", "brand", "field", "name", "value", request.name()));
        }

        if (request.imageId() != null) {
            Media image = mediaRepository
                    .findPublicTempMediaById(request.imageId(), MediaResourceType.BRAND)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            Map.of("resourceType", "media", "resourceId", request.imageId())));
            image.setTempFlg(false);
            brand.setImage(image);
        }
        if (request.categoryIds() != null) {
            List<Category> categories = fetchCategories(request.categoryIds());
            brand.setCategories(categories);
        }

        Brand savedBrand = brandRepository.save(brand);
        return BrandMapper.INSTANCE.toBrandDetailResponse(savedBrand);
    }

    @Transactional
    @Override
    public BrandDetailResponse updateBrand(Integer id, UpdateBrandRequest request) {
        Brand brand = brandRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "brand", "resourceId", id)));

        if (brand.getUpdatedAt() != null && !request.lastRetrievedAt().isAfter(brand.getUpdatedAt())) {
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

        brand.setPublicFlg(request.publicFlg());

        // Update image
        updateImage(brand, request.imageId(), MediaResourceType.BRAND);

        // Update categories
        if (request.categoryIds() != null) {
            List<Category> categories = fetchCategories(request.categoryIds());
            brand.setCategories(categories);
        }
        Brand savedBrand = brandRepository.save(brand);
        return BrandMapper.INSTANCE.toBrandDetailResponse(savedBrand);
    }

    @Transactional
    @Override
    public void deleteBrand(Integer id, ModifyExclusiveDTO request) {
        Brand brand = brandRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "brand", "resourceId", id)));
        if (!request.lastRetrievedAt().isAfter(brand.getUpdatedAt())) {
            throw new ApplicationException(
                    ErrorCode.CONCURRENT_MODIFICATION, Map.of("resourceType", "brand", "resourceId", id));
        }
        //        long countBrands = brandRepository.countBrandsInOtherTables(id);
        //        if (countBrands > 0){
        //            throw new BadRequestException(i18nUtils.getBrandCannotDeleteMsg(existingBrand.getName()));
        //        }
        if (brand.getImage() != null) {
            Media image = brand.getImage();
            image.setDeleteFlg(true);
            mediaRepository.save(image);
        }
        brandRepository.delete(brand);
    }

    @Override
    public CheckExistResponse checkBrandExist(BrandExistRequest request) {
        return new CheckExistResponse("name", brandRepository.existsByName(request.name()));
    }

    private List<Category> fetchCategories(Set<Integer> categoryIds) {
        return categoryIds.stream()
                .map(categoryId -> categoryRepository
                        .findPublicById(categoryId)
                        .orElseThrow(() -> new ApplicationException(
                                ErrorCode.RESOURCE_NOT_FOUND,
                                Map.of("resourceType", "category", "resourceId", categoryId))))
                .collect(Collectors.toList());
    }
}
