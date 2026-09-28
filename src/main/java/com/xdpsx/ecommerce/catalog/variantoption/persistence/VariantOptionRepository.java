package com.xdpsx.ecommerce.catalog.variantoption.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOption;

public interface VariantOptionRepository extends JpaRepository<VariantOption, Long> {
    boolean existsByCode(String code);

    @Query("SELECT DISTINCT o FROM VariantOption o LEFT JOIN FETCH o.values WHERE o.id = :id")
    Optional<VariantOption> findByIdWithValues(@Param("id") Long id);

    @Query("SELECT DISTINCT o FROM VariantOption o LEFT JOIN FETCH o.values ORDER BY o.displayOrder ASC, o.id ASC")
    List<VariantOption> findAllWithValues();
}
