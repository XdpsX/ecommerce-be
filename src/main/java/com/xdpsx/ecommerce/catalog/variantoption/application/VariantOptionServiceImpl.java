package com.xdpsx.ecommerce.catalog.variantoption.application;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.api.dto.*;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOption;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VariantOptionServiceImpl implements VariantOptionService {
    private final VariantOptionRepository optionRepository;
    private final VariantOptionValueRepository valueRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(readOnly = true)
    @Override
    public List<VariantOptionResponse> getVariantOptions() {
        return optionRepository.findAllWithValues().stream()
                .map(VariantOptionServiceImpl::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public VariantOptionResponse getVariantOption(Long id) {
        return optionRepository
                .findByIdWithValues(id)
                .map(VariantOptionServiceImpl::toResponse)
                .orElseThrow(() -> notFound("variant-option", id));
    }

    @Transactional
    @Override
    public VariantOptionResponse createVariantOption(CreateVariantOptionRequest request) {
        String code = normalizeCode(request.code());
        if (optionRepository.existsByCode(code)) {
            throw duplicate("variant-option", "code", code);
        }

        List<CreateVariantOptionValueRequest> requestedValues = request.values() == null ? List.of() : request.values();
        List<NormalizedValue> values = normalizeValues(requestedValues);
        VariantOption option = VariantOption.builder()
                .code(code)
                .name(normalizeName(request.name()))
                .displayOrder(defaultOrder(request.displayOrder()))
                .status(defaultStatus(request.status()))
                .build();

        try {
            optionRepository.saveAndFlush(option);
            for (NormalizedValue value : values) {
                valueRepository.save(VariantOptionValue.builder()
                        .option(option)
                        .code(value.code())
                        .name(value.name())
                        .displayOrder(value.displayOrder())
                        .status(value.status())
                        .build());
            }
            valueRepository.flush();
            return getVariantOption(option.getId());
        } catch (DataIntegrityViolationException exception) {
            if (isConstraint(exception, "variant_option_code")) {
                throw duplicate("variant-option", "code", code, exception);
            }
            if (isConstraint(exception, "variant_option_value_code")) {
                throw duplicate("variant-option-value", "code", "value", exception);
            }
            if (isConstraint(exception, "variant_option_value_order")) {
                throw duplicate("variant-option-value", "displayOrder", "value", exception);
            }
            throw exception;
        }
    }

    @Transactional
    @Override
    public VariantOptionResponse updateVariantOption(Long id, UpdateVariantOptionRequest request) {
        VariantOption option = optionRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("variant-option", id));
        rejectDeactivationWhenReferenced(option.getStatus(), request.status(), "variant-option", id, true);
        option.setName(normalizeName(request.name()));
        option.setDisplayOrder(request.displayOrder());
        option.setStatus(request.status());
        optionRepository.saveAndFlush(option);
        return getVariantOption(id);
    }

    @Transactional
    @Override
    public VariantOptionValueResponse addValue(Long optionId, CreateVariantOptionValueRequest request) {
        VariantOption option =
                optionRepository.findByIdWithValues(optionId).orElseThrow(() -> notFound("variant-option", optionId));
        String code = normalizeCode(request.code());
        if (valueRepository.existsByOptionIdAndCode(optionId, code)) {
            throw duplicate("variant-option-value", "code", code);
        }
        int displayOrder = request.displayOrder() == null
                ? option.getValues().stream()
                                .map(VariantOptionValue::getDisplayOrder)
                                .filter(Objects::nonNull)
                                .mapToInt(Integer::intValue)
                                .max()
                                .orElse(-1)
                        + 1
                : request.displayOrder();
        VariantOptionValue value = VariantOptionValue.builder()
                .option(option)
                .code(code)
                .name(normalizeName(request.name()))
                .displayOrder(displayOrder)
                .status(defaultStatus(request.status()))
                .build();
        try {
            valueRepository.saveAndFlush(value);
            return toValueResponse(value);
        } catch (DataIntegrityViolationException exception) {
            if (isConstraint(exception, "variant_option_value_code")) {
                throw duplicate("variant-option-value", "code", code, exception);
            }
            if (isConstraint(exception, "variant_option_value_order")) {
                throw duplicate("variant-option-value", "displayOrder", displayOrder, exception);
            }
            throw exception;
        }
    }

    @Transactional
    @Override
    public VariantOptionValueResponse updateValue(
            Long optionId, Long valueId, UpdateVariantOptionValueRequest request) {
        optionRepository.findByIdForUpdate(optionId).orElseThrow(() -> notFound("variant-option", optionId));
        VariantOptionValue value = valueRepository
                .findByIdAndOptionIdForUpdate(valueId, optionId)
                .orElseThrow(() -> notFound("variant-option-value", valueId));
        rejectDeactivationWhenReferenced(value.getStatus(), request.status(), "variant-option-value", valueId, false);
        value.setName(normalizeName(request.name()));
        value.setDisplayOrder(request.displayOrder());
        value.setStatus(request.status());
        try {
            valueRepository.saveAndFlush(value);
            return toValueResponse(value);
        } catch (DataIntegrityViolationException exception) {
            if (isConstraint(exception, "variant_option_value_order")) {
                throw duplicate("variant-option-value", "displayOrder", request.displayOrder(), exception);
            }
            throw exception;
        }
    }

    @Transactional
    @Override
    public List<VariantOptionValueResponse> reorderValues(Long optionId, ReorderVariantOptionValuesRequest request) {
        VariantOption option =
                optionRepository.findByIdWithValues(optionId).orElseThrow(() -> notFound("variant-option", optionId));
        List<VariantOptionValue> values = option.getValues();
        Set<Long> requestedIds = new HashSet<>(request.valueIds());
        Set<Long> existingIds = values.stream().map(VariantOptionValue::getId).collect(Collectors.toSet());
        if (requestedIds.size() != request.valueIds().size() || !requestedIds.equals(existingIds)) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "valueIds", "reason", "must contain every value exactly once"));
        }

        // Move every row to a disjoint temporary range before assigning final indexes. This makes a direct A/B
        // swap safe even though (option_id, display_order) is unique.
        for (int index = 0; index < values.size(); index++) {
            values.get(index).setDisplayOrder(-index - 1);
        }
        valueRepository.flush();
        Map<Long, VariantOptionValue> byId =
                values.stream().collect(Collectors.toMap(VariantOptionValue::getId, value -> value));
        for (int index = 0; index < request.valueIds().size(); index++) {
            byId.get(request.valueIds().get(index)).setDisplayOrder(index);
        }
        valueRepository.flush();
        return request.valueIds().stream()
                .map(id -> toValueResponse(byId.get(id)))
                .collect(Collectors.toList());
    }

    private void rejectDeactivationWhenReferenced(
            VariantOptionStatus currentStatus,
            VariantOptionStatus requestedStatus,
            String resourceType,
            Long resourceId,
            boolean option) {
        if (currentStatus == VariantOptionStatus.ACTIVE && requestedStatus == VariantOptionStatus.INACTIVE) {
            boolean referenced = option
                    ? productVariantRepository.existsActiveReferenceToOption(resourceId)
                    : productVariantRepository.existsActiveReferenceToValue(resourceId);
            if (referenced) {
                throw new ApplicationException(
                        ErrorCode.RESOURCE_IN_USE, Map.of("resourceType", resourceType, "resourceId", resourceId));
            }
        }
    }

    private static List<NormalizedValue> normalizeValues(List<CreateVariantOptionValueRequest> requests) {
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    CreateVariantOptionValueRequest request = requests.get(index);
                    String code = normalizeCode(request.code());
                    if (!codes.add(code)) {
                        throw duplicate("variant-option-value", "code", code);
                    }
                    int order = request.displayOrder() == null ? index : request.displayOrder();
                    if (!orders.add(order)) {
                        throw duplicate("variant-option-value", "displayOrder", order);
                    }
                    return new NormalizedValue(
                            code, normalizeName(request.name()), order, defaultStatus(request.status()));
                })
                .collect(Collectors.toList());
    }

    private static VariantOptionResponse toResponse(VariantOption option) {
        List<VariantOptionValueResponse> values = option.getValues().stream()
                .map(VariantOptionServiceImpl::toValueResponse)
                .collect(Collectors.toList());
        return new VariantOptionResponse(
                option.getId(),
                option.getCode(),
                option.getName(),
                option.getDisplayOrder(),
                option.getStatus(),
                values);
    }

    private static VariantOptionValueResponse toValueResponse(VariantOptionValue value) {
        return new VariantOptionValueResponse(
                value.getId(), value.getCode(), value.getName(), value.getDisplayOrder(), value.getStatus());
    }

    private static String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String name) {
        return name.trim();
    }

    private static int defaultOrder(Integer order) {
        return order == null ? 0 : order;
    }

    private static VariantOptionStatus defaultStatus(VariantOptionStatus status) {
        return status == null ? VariantOptionStatus.ACTIVE : status;
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

    private static boolean isConstraint(DataIntegrityViolationException exception, String key) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).contains(key)) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(key)) {
                return true;
            }
        }
        return false;
    }

    private record NormalizedValue(String code, String name, int displayOrder, VariantOptionStatus status) {}
}
