package com.xdpsx.ecommerce.catalog.variantoption.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;

public interface VariantOptionValueRepository extends JpaRepository<VariantOptionValue, Long> {
    boolean existsByOptionIdAndCode(Long optionId, String code);

    boolean existsByOptionIdAndCodeAndIdNot(Long optionId, String code, Long id);

    Optional<VariantOptionValue> findByIdAndOptionId(Long id, Long optionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VariantOptionValue v JOIN FETCH v.option WHERE v.id = :valueId AND v.option.id = :optionId")
    Optional<VariantOptionValue> findByIdAndOptionIdForUpdate(
            @Param("valueId") Long valueId, @Param("optionId") Long optionId);

    @Query("SELECT v.option.id FROM VariantOptionValue v WHERE v.id IN :ids ORDER BY v.option.id ASC")
    List<Long> findOptionIdsByValueIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT v FROM VariantOptionValue v JOIN FETCH v.option " + "WHERE v.id IN :ids")
    List<VariantOptionValue> findAllByIdInWithOption(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT v FROM VariantOptionValue v JOIN FETCH v.option WHERE v.id IN :ids ORDER BY v.option.id ASC, v.id ASC")
    List<VariantOptionValue> findAllByIdInWithOptionForUpdate(@Param("ids") Collection<Long> ids);
}
