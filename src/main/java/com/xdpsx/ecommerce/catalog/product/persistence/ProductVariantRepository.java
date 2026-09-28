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
