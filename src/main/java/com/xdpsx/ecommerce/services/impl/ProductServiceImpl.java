package com.xdpsx.ecommerce.services.impl;

import static com.xdpsx.ecommerce.constants.FileConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.xdpsx.ecommerce.dtos.common.PageResponse;
import com.xdpsx.ecommerce.dtos.media.CloudinaryUploadResponse;
import com.xdpsx.ecommerce.dtos.product.*;
import com.xdpsx.ecommerce.entities.Brand;
import com.xdpsx.ecommerce.entities.Category;
import com.xdpsx.ecommerce.entities.Product;
import com.xdpsx.ecommerce.entities.ProductImage;
import com.xdpsx.ecommerce.exceptions.BadRequestException;
import com.xdpsx.ecommerce.exceptions.DuplicateException;
import com.xdpsx.ecommerce.exceptions.NotFoundException;
import com.xdpsx.ecommerce.mappers.PageMapper;
import com.xdpsx.ecommerce.mappers.ProductMapper;
import com.xdpsx.ecommerce.repositories.BrandRepository;
import com.xdpsx.ecommerce.repositories.CategoryRepository;
import com.xdpsx.ecommerce.repositories.ProductRepository;
import com.xdpsx.ecommerce.repositories.specs.ProductSpecification;
import com.xdpsx.ecommerce.services.ProductService;
import com.xdpsx.ecommerce.utils.CloudinaryUploader;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;
    private final PageMapper pageMapper;
    private final CloudinaryUploader uploader;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductSpecification spec;

    private static final Map uploadOptions = ObjectUtils.asMap(
            "folder",
            PRODUCT_IMG_FOLDER,
            "transformation",
            new Transformation().width(PRODUCT_IMG_WIDTH).crop("scale"));

    @Override
    public PageResponse<ProductResponse> filterAllProducts(ProductParams params) {
        Page<Product> productPage = productRepository.findAll(
                spec.getFiltersSpec(
                        params.getSearch(),
                        params.getSort(),
                        params.getHasPublished(),
                        params.getMinPrice(),
                        params.getMaxPrice(),
                        params.getHasDiscount(),
                        params.getInStock(),
                        params.getCategoryId(),
                        params.getBrandId()),
                PageRequest.of(params.getPageNum() - 1, params.getPageSize()));
        return pageMapper.toProductPageResponse(productPage);
    }

    @Override
    public ProductDetailsDTO getProductById(Long id) {
        Product product = productRepository
                .findProductById(id)
                .orElseThrow(() -> new NotFoundException("Product with id=%s not found!".formatted(id)));
        return productMapper.fromEntityToDetailsDTO(product);
    }

    @Override
    public ProductDetailsDTO getProductBySlug(String slug) {
        Product product = productRepository
                .findProductBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Product with slug=%s not found!".formatted(slug)));
        return productMapper.fromEntityToDetailsDTO(product);
    }

    @Override
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsBySlug(request.getSlug())) {
            throw new DuplicateException("Product with slug=%s already exists".formatted(request.getSlug()));
        }
        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new NotFoundException("Category with id=%s not found!".formatted(request.getCategoryId())));
        Brand brand = brandRepository
                .findById(request.getBrandId())
                .orElseThrow(
                        () -> new NotFoundException("Brand with id=%s not found!".formatted(request.getBrandId())));

        Product product = productMapper.fromCreateRequestToEntity(request);
        product.setCategory(category);
        product.setBrand(brand);

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            product.setImages(new ArrayList<>());
            uploadProductImages(request.getImages(), product);
            product.setMainImage(product.getImages().get(0).getUrl());
        }

        Product savedProduct = productRepository.save(product);
        return productMapper.fromEntityToResponse(savedProduct);
    }

    @Override
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository
                .findProductById(id)
                .orElseThrow(() -> new NotFoundException("Product with id=%s not found!".formatted(id)));

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDiscountPercent(request.getDiscountPercent());
        product.setInStock(request.isInStock());
        product.setPublished(request.isPublished());
        product.setDescription(request.getDescription());

        if (!request.getSlug().equals(product.getSlug())) {
            if (productRepository.existsBySlug(request.getSlug())) {
                throw new DuplicateException("Product with slug=%s already exists".formatted(request.getSlug()));
            }
            product.setSlug(request.getSlug());
        }

        if (request.getCategoryId() != null && !product.getCategory().getId().equals(request.getCategoryId())) {
            Category category = categoryRepository
                    .findById(request.getCategoryId())
                    .orElseThrow(() ->
                            new NotFoundException("Category with id=%s not found!".formatted(request.getCategoryId())));
            product.setCategory(category);
        }

        if (request.getBrandId() != null && !product.getBrand().getId().equals(request.getBrandId())) {
            Brand brand = brandRepository
                    .findById(request.getBrandId())
                    .orElseThrow(
                            () -> new NotFoundException("Brand with id=%s not found!".formatted(request.getBrandId())));
            product.setBrand(brand);
        }

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            if (request.getImages().size() + product.getImages().size() > NUMBER_PRODUCT_IMAGES) {
                throw new BadRequestException("Product can not have more than " + NUMBER_PRODUCT_IMAGES + " images");
            }
            uploadProductImages(request.getImages(), product);
        }

        if (request.getRemovedImageIds() != null
                && !request.getRemovedImageIds().isEmpty()) {
            product.getImages().removeIf(image -> {
                if (request.getRemovedImageIds().contains(image.getId())) {
                    uploader.deleteFile(image.getUrl());
                    // remove main image if image is removed
                    if (image.getUrl().equals(product.getMainImage())) {
                        product.setMainImage(null);
                    }
                    return true;
                }
                return false;
            });
            // update main image if it is removed
            if (product.getMainImage() == null && !product.getImages().isEmpty()) {
                product.setMainImage(product.getImages().get(0).getUrl());
            }
        }

        Product updatedProduct = productRepository.save(product);
        return productMapper.fromEntityToResponse(updatedProduct);
    }

    @Override
    public void deleteProduct(Long id) {
        Product product = productRepository
                .findProductById(id)
                .orElseThrow(() -> new NotFoundException("Product with id=%s not found!".formatted(id)));
        productRepository.delete(product);
        for (ProductImage productImage : product.getImages()) {
            uploader.deleteFile(productImage.getUrl());
        }
    }

    @Override
    public void publishProduct(Long id, boolean status) {
        Product product = productRepository
                .findProductById(id)
                .orElseThrow(() -> new NotFoundException("Product with id=%s not found!".formatted(id)));
        product.setPublished(status);
        productRepository.save(product);
    }

    @Override
    public Map<String, Boolean> checkExistsProduct(String slug) {
        Map<String, Boolean> exists = new HashMap<>();
        exists.put("slugExists", productRepository.existsBySlug(slug));
        return exists;
    }

    @Override
    public PageResponse<ProductResponse> getDiscountProducts(int pageNum, int pageSize) {
        Specification<Product> prodSpec =
                Specification.where(spec.hasDiscount(true)).and(spec.hasPublished(true));
        Page<Product> productPage = productRepository.findAll(prodSpec, PageRequest.of(pageNum - 1, pageSize));
        return pageMapper.toProductPageResponse(productPage);
    }

    @Override
    public PageResponse<ProductResponse> getLatestProducts(int pageNum, int pageSize) {
        Specification<Product> prodSpec =
                Specification.where(spec.getSortSpec("-date")).and(spec.hasPublished(true));
        Page<Product> productPage = productRepository.findAll(prodSpec, PageRequest.of(pageNum - 1, pageSize));
        return pageMapper.toProductPageResponse(productPage);
    }

    @Override
    public PageResponse<ProductResponse> getProductsByCategoryId(
            Integer categoryId,
            int pageNum,
            int pageSize,
            List<Integer> brandIds,
            String sort,
            Double minPrice,
            Double maxPrice) {
        Category category = categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id=%s not found!".formatted(categoryId)));
        Specification<Product> prodSpec = Specification.where(spec.belongsToCategory(categoryId))
                .and(spec.belongsToBrands(brandIds))
                .and(spec.hasPublished(true))
                .and(spec.getSortSpec(sort))
                .and(spec.hasMinPrice(minPrice))
                .and(spec.hasMaxPrice(maxPrice));
        Page<Product> productPage = productRepository.findAll(prodSpec, PageRequest.of(pageNum - 1, pageSize));
        return pageMapper.toProductPageResponse(productPage);
    }

    private void uploadProductImages(List<MultipartFile> files, Product product) {
        for (MultipartFile file : files) {
            CloudinaryUploadResponse response = uploader.uploadFile(file, uploadOptions);
            ProductImage productImage =
                    ProductImage.builder().url(response.url()).product(product).build();
            product.getImages().add(productImage);
        }
    }
}
