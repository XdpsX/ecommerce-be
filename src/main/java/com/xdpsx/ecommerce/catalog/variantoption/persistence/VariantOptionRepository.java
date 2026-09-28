package com.xdpsx.ecommerce.catalog.variantoption.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOption;

public interface VariantOptionRepository extends JpaRepository<VariantOption, Long> {
    boolean existsByCode(String code);

    @Query("SELECT DISTINCT o FROM VariantOption o LEFT JOIN FETCH o.values WHERE o.id = :id")
    Optional<VariantOption> findByIdWithValues(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM VariantOption o WHERE o.id = :id")
    Optional<VariantOption> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM VariantOption o WHERE o.id IN :ids ORDER BY o.id ASC")
    List<VariantOption> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    @Query("SELECT DISTINCT o FROM VariantOption o LEFT JOIN FETCH o.values ORDER BY o.displayOrder ASC, o.id ASC")
    List<VariantOption> findAllWithValues();
}
