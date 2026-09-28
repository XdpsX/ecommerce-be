package com.xdpsx.ecommerce.catalog.variantoption.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;

public interface VariantOptionValueRepository extends JpaRepository<VariantOptionValue, Long> {
    boolean existsByOptionIdAndCode(Long optionId, String code);

    boolean existsByOptionIdAndCodeAndIdNot(Long optionId, String code, Long id);

    Optional<VariantOptionValue> findByIdAndOptionId(Long id, Long optionId);
}
