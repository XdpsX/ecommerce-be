package com.xdpsx.ecommerce.catalog.product.application;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.domain.*;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.inventory.application.InventoryProvisioningService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final VariantOptionRepository optionRepository;
    private final VariantOptionValueRepository optionValueRepository;
    private final EntityManager entityManager;
    private final InventoryProvisioningService inventoryProvisioningService;

    @Transactional(readOnly = true)
    @Override
    public List<ProductVariantResponse> getVariants(Long productId) {
        requireProduct(productId);
        return variantRepository.findAllWithSelectionsByProductId(productId).stream()
                .map(ProductVariantServiceImpl::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public List<ProductVariantResponse> createVariants(Long productId, ProductVariantBatchCreateRequest request) {
        Product product = lockProduct(productId);
        List<ProductVariantCreateRequest> requested = request.variants();
        List<String> skus =
                requested.stream().map(item -> normalizeSku(item.sku())).toList();
        rejectDuplicates(skus, "sku");
        if (skus.stream().anyMatch(variantRepository::existsBySku)) {
            throw duplicate(
                    "product-variant",
                    "sku",
                    skus.stream()
                            .filter(variantRepository::existsBySku)
                            .findFirst()
                            .orElse(""));
        }

        List<String> barcodes = requested.stream()
                .map(item -> normalizeBarcode(item.barcode()))
                .filter(Objects::nonNull)
                .toList();
        rejectDuplicates(barcodes, "barcode");
        if (barcodes.stream().anyMatch(variantRepository::existsByBarcode)) {
            throw duplicate(
                    "product-variant",
                    "barcode",
                    barcodes.stream()
                            .filter(variantRepository::existsByBarcode)
                            .findFirst()
                            .orElse(""));
        }

        List<Long> allValueIds = requested.stream()
                .flatMap(item -> item.optionValueIds().stream())
                .toList();
        lockDictionaryRows(allValueIds);
        Map<Long, VariantOptionValue> valuesById = loadActiveValues(allValueIds);
        List<ProductVariant> existingVariants = variantRepository.findAllWithSelectionsByProductId(productId);
        Set<Long> existingOptionSet = existingVariants.stream()
                .filter(item -> item.getStatus() == ProductVariantStatus.ACTIVE)
                .findFirst()
                .map(ProductVariantServiceImpl::optionIds)
                .orElse(null);

        Set<String> combinations = new HashSet<>();
        List<ProductVariant> variants = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            ProductVariantCreateRequest item = requested.get(index);
            List<VariantOptionValue> selectedValues = resolveSelectedValues(item.optionValueIds(), valuesById);
            Set<Long> optionIds = selectedValues.stream()
                    .map(value -> value.getOption().getId())
                    .collect(Collectors.toCollection(TreeSet::new));
            if (optionIds.size() != selectedValues.size()) {
                throw invalid(
                        "variants[" + index + "].optionValueIds", "a variant may select only one value per option");
            }
            if (existingOptionSet != null && !existingOptionSet.equals(optionIds)) {
                throw invalid("variants[" + index + "].optionValueIds", "active variants must use the same option set");
            }
            if (existingOptionSet == null) existingOptionSet = optionIds;

            String combinationKey = combinationKey(selectedValues);
            if (!combinations.add(combinationKey)
                    || variantRepository.existsByProductIdAndCombinationKey(productId, combinationKey)) {
                throw duplicate("product-variant", "combination", combinationKey);
            }
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(skus.get(index))
                    .barcode(barcodesForIndex(requested, index))
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey(combinationKey)
                    .build();
            for (VariantOptionValue value : selectedValues) {
                variant.getSelections()
                        .add(ProductVariantSelection.builder()
                                .id(new ProductVariantSelectionId(
                                        null, value.getOption().getId()))
                                .variant(variant)
                                .optionValueId(value.getId())
                                .optionValue(value)
                                .build());
            }
            variants.add(variant);
        }

        try {
            variantRepository.saveAll(variants);
            variantRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
        inventoryProvisioningService.provisionBalances(variants);
        return variants.stream().map(ProductVariantServiceImpl::toResponse).toList();
    }

    @Transactional
    @Override
    public ProductVariantResponse updateBarcode(
            Long productId, Long variantId, UpdateProductVariantBarcodeRequest request) {
        lockProduct(productId);
        ProductVariant variant = findVariant(productId, variantId);
        String barcode = normalizeBarcode(request.barcode());
        if (barcode != null && variantRepository.existsByBarcodeAndIdNot(barcode, variantId)) {
            throw duplicate("product-variant", "barcode", barcode);
        }
        variant.setBarcode(barcode);
        try {
            variantRepository.saveAndFlush(variant);
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
        return toResponse(variant);
    }

    @Transactional
    @Override
    public ProductVariantResponse updateStatus(
            Long productId, Long variantId, UpdateProductVariantStatusRequest request) {
        Product product = lockProduct(productId);
        ProductVariant variant = findVariant(productId, variantId);
        ProductVariantStatus target = request.status();
        if (variant.getStatus() == ProductVariantStatus.ACTIVE && target == ProductVariantStatus.INACTIVE) {
            if (product.isPublished()
                    && variantRepository.countByProductIdAndStatus(productId, ProductVariantStatus.ACTIVE) <= 1) {
                throw new ApplicationException(
                        ErrorCode.RESOURCE_IN_USE,
                        Map.of(
                                "resourceType",
                                "product-variant",
                                "resourceId",
                                variantId,
                                "reason",
                                "published product requires an active variant"));
            }
        } else if (variant.getStatus() == ProductVariantStatus.INACTIVE && target == ProductVariantStatus.ACTIVE) {
            validateActiveSelections(productId, variant);
        }
        variant.setStatus(target);
        variantRepository.saveAndFlush(variant);
        return toResponse(variant);
    }

    private Map<Long, VariantOptionValue> loadActiveValues(List<Long> ids) {
        Set<Long> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.isEmpty()) return Map.of();
        List<VariantOptionValue> values = optionValueRepository.findAllByIdInWithOptionForUpdate(uniqueIds);
        values.forEach(value -> {
            entityManager.refresh(value.getOption());
            entityManager.refresh(value);
        });
        if (values.size() != uniqueIds.size()) {
            throw invalid("optionValueIds", "every option value must exist and be active");
        }
        if (values.stream()
                .anyMatch(value ->
                        value.getStatus() != com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE
                                || value.getOption().getStatus() != VariantOptionStatus.ACTIVE)) {
            throw invalid("optionValueIds", "every option value and its option must be active");
        }
        return values.stream().collect(Collectors.toMap(VariantOptionValue::getId, value -> value));
    }

    private void lockDictionaryRows(Collection<Long> valueIds) {
        Set<Long> uniqueValueIds = new HashSet<>(valueIds);
        if (uniqueValueIds.isEmpty()) return;
        List<Long> optionIds = optionValueRepository.findOptionIdsByValueIds(uniqueValueIds).stream()
                .distinct()
                .sorted()
                .toList();
        optionRepository.findAllByIdInForUpdate(optionIds);
        optionValueRepository.findAllByIdInWithOptionForUpdate(uniqueValueIds);
    }

    private static List<VariantOptionValue> resolveSelectedValues(
            List<Long> ids, Map<Long, VariantOptionValue> valuesById) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw invalid("optionValueIds", "must not contain duplicate values in one variant");
        }
        return ids.stream()
                .map(valuesById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(value -> value.getOption().getId()))
                .collect(Collectors.toList());
    }

    private static String combinationKey(List<VariantOptionValue> values) {
        return values.stream()
                .map(value -> value.getOption().getId() + "=" + value.getId())
                .collect(Collectors.joining("|"));
    }

    private static Set<Long> optionIds(ProductVariant variant) {
        return variant.getSelections().stream()
                .map(selection -> selection.getId().getOptionId())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private void validateActiveSelections(Long productId, ProductVariant variant) {
        Set<Long> selectedOptionIds = optionIds(variant);
        List<ProductVariant> activeVariants = variantRepository.findAllWithSelectionsByProductId(productId).stream()
                .filter(item -> item.getStatus() == ProductVariantStatus.ACTIVE)
                .toList();
        if (!activeVariants.isEmpty()
                && activeVariants.stream().anyMatch(item -> !selectedOptionIds.equals(optionIds(item)))) {
            throw invalid("status", "active variants must use the same option set");
        }

        List<Long> selectedValueIds = variant.getSelections().stream()
                .map(ProductVariantSelection::getOptionValueId)
                .sorted()
                .toList();
        lockDictionaryRows(selectedValueIds);
        Map<Long, VariantOptionValue> lockedValues = loadActiveValues(selectedValueIds);
        if (variant.getSelections().stream().anyMatch(selection -> {
            VariantOptionValue value = lockedValues.get(selection.getOptionValueId());
            return value == null
                    || !selection.getId().getOptionId().equals(value.getOption().getId());
        })) {
            throw invalid("status", "a variant can only be activated while all option values are active");
        }
        if (lockedValues.values().stream()
                .anyMatch(value -> value.getStatus() != VariantOptionStatus.ACTIVE
                        || value.getOption().getStatus() != VariantOptionStatus.ACTIVE)) {
            throw invalid("status", "a variant can only be activated while all option values are active");
        }
    }

    private ProductVariant findVariant(Long productId, Long variantId) {
        return variantRepository
                .findByIdAndProductIdWithSelections(variantId, productId)
                .orElseThrow(() -> notFound("product-variant", variantId));
    }

    private Product lockProduct(Long productId) {
        return productRepository.findByIdForUpdate(productId).orElseThrow(() -> notFound("product", productId));
    }

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) throw notFound("product", productId);
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeBarcode(String barcode) {
        if (barcode == null) return null;
        String normalized = barcode.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String barcodesForIndex(List<ProductVariantCreateRequest> requests, int index) {
        return normalizeBarcode(requests.get(index).barcode());
    }

    private static void rejectDuplicates(List<String> values, String field) {
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (!unique.add(value)) throw duplicate("product-variant", field, value);
        }
    }

    private static ApplicationException translateConstraint(DataIntegrityViolationException exception) {
        String key = constraintKey(exception);
        if (key.contains("sku")) return duplicate("product-variant", "sku", "value", exception);
        if (key.contains("barcode")) return duplicate("product-variant", "barcode", "value", exception);
        if (key.contains("combination")) return duplicate("product-variant", "combination", "value", exception);
        return new ApplicationException(
                ErrorCode.RESOURCE_ALREADY_EXISTS, Map.of("resourceType", "product-variant"), exception);
    }

    private static String constraintKey(DataIntegrityViolationException exception) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                text.append(violation.getConstraintName());
            }
            if (cause.getMessage() != null) text.append(cause.getMessage());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static ProductVariantResponse toResponse(ProductVariant variant) {
        List<Long> optionValueIds = variant.getSelections().stream()
                .sorted(Comparator.comparing(selection -> selection.getId().getOptionId()))
                .map(ProductVariantSelection::getOptionValueId)
                .toList();
        return new ProductVariantResponse(
                variant.getId(), variant.getSku(), variant.getBarcode(), variant.getStatus(), optionValueIds);
    }

    private static ApplicationException notFound(String type, Long id) {
        return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", type, "resourceId", id));
    }

    private static ApplicationException duplicate(String type, String field, Object value) {
        return duplicate(type, field, value, null);
    }

    private static ApplicationException duplicate(String type, String field, Object value, Throwable cause) {
        return new ApplicationException(
                ErrorCode.RESOURCE_ALREADY_EXISTS, Map.of("resourceType", type, "field", field, "value", value), cause);
    }

    private static ApplicationException invalid(String field, String reason) {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", field, "reason", reason));
    }
}
