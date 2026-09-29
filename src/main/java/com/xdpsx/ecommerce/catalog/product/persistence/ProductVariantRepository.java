package com.xdpsx.ecommerce.catalog.product.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    boolean existsByProductIdAndCombinationKey(Long productId, String combinationKey);

    List<ProductVariant> findAllByProductIdAndStatus(Long productId, ProductVariantStatus status);

    @Query("SELECT DISTINCT v FROM ProductVariant v "
            + "LEFT JOIN FETCH v.selections s "
            + "WHERE v.product.id = :productId ORDER BY v.id ASC")
    List<ProductVariant> findAllWithSelectionsByProductId(@Param("productId") Long productId);

    @Query("SELECT DISTINCT v FROM ProductVariant v "
            + "LEFT JOIN FETCH v.selections s "
            + "LEFT JOIN FETCH s.optionValue value "
            + "LEFT JOIN FETCH value.option option "
            + "WHERE v.product.id = :productId "
            + "AND v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "ORDER BY v.id ASC")
    List<ProductVariant> findActiveWithSelectionsAndOptionsByProductId(@Param("productId") Long productId);

    interface FilterOptionValueView {
        Long getOptionId();

        String getOptionCode();

        String getOptionName();

        Integer getOptionDisplayOrder();

        Long getValueId();

        String getValueCode();

        String getValueName();

        Integer getValueDisplayOrder();
    }

    @Query("SELECT DISTINCT "
            + "option.id AS optionId, option.code AS optionCode, option.name AS optionName, "
            + "option.displayOrder AS optionDisplayOrder, value.id AS valueId, value.code AS valueCode, "
            + "value.name AS valueName, value.displayOrder AS valueDisplayOrder "
            + "FROM ProductVariantSelection selection "
            + "JOIN selection.variant variant "
            + "JOIN selection.optionValue value "
            + "JOIN value.option option "
            + "JOIN variant.product product "
            + "JOIN product.category category "
            + "LEFT JOIN category.parent categoryParent "
            + "LEFT JOIN categoryParent.parent categoryGrandparent "
            + "LEFT JOIN categoryGrandparent.parent categoryGreatGrandparent "
            + "JOIN product.brand brand "
            + "WHERE variant.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND product.published = true "
            + "AND brand.status = com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus.ACTIVE "
            + "AND category.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE "
            + "AND (categoryParent.id IS NULL OR categoryParent.status = "
            + "com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND (categoryGrandparent.id IS NULL OR categoryGrandparent.status = "
            + "com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND categoryGreatGrandparent.id IS NULL "
            + "AND option.status = com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE "
            + "AND value.status = com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE "
            + "AND (:categoryId IS NULL OR product.category.id = :categoryId) "
            + "AND (:brandId IS NULL OR product.brand.id = :brandId) "
            + "ORDER BY option.displayOrder ASC, option.id ASC, value.displayOrder ASC, value.id ASC")
    List<FilterOptionValueView> findActiveFilterOptionValues(
            @Param("categoryId") Integer categoryId, @Param("brandId") Integer brandId);

    @Query("SELECT DISTINCT v FROM ProductVariant v "
            + "LEFT JOIN FETCH v.selections s "
            + "WHERE v.id = :variantId AND v.product.id = :productId")
    Optional<ProductVariant> findByIdAndProductIdWithSelections(
            @Param("variantId") Long variantId, @Param("productId") Long productId);

    @Query("SELECT v FROM ProductVariant v WHERE v.product.id = :productId AND v.status = :status")
    List<ProductVariant> findByProductIdAndStatus(
            @Param("productId") Long productId, @Param("status") ProductVariantStatus status);

    @Query("SELECT COUNT(v) > 0 FROM ProductVariant v JOIN v.selections s "
            + "WHERE v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND s.id.optionId = :optionId")
    boolean existsActiveReferenceToOption(@Param("optionId") Long optionId);

    @Query("SELECT COUNT(v) > 0 FROM ProductVariant v JOIN v.selections s "
            + "WHERE v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND s.optionValueId = :optionValueId")
    boolean existsActiveReferenceToValue(@Param("optionValueId") Long optionValueId);

    @Query("SELECT COUNT(v) > 0 FROM ProductVariant v "
            + "WHERE v.product.id = :productId "
            + "AND v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE")
    boolean existsActiveByProductId(@Param("productId") Long productId);

    long countByProductIdAndStatus(Long productId, ProductVariantStatus status);

    boolean existsBySkuIn(Collection<String> skus);

    boolean existsByBarcodeIn(Collection<String> barcodes);
}
