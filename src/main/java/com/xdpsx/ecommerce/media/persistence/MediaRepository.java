package com.xdpsx.ecommerce.media.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;

public interface MediaRepository extends CrudRepository<Media, String> {
    @Query("SELECT m FROM Media m WHERE m.status = :status")
    List<Media> findAllByStatus(@Param("status") MediaStatus status);

    @Query("""
		SELECT m FROM Media m
		WHERE m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.TEMPORARY
			AND m.createdAt < :expiryTime
		""")
    List<Media> findExpiredTemporaryMedia(@Param("expiryTime") LocalDateTime expiryTime);

    @Query("""
		SELECT m FROM Media m
		WHERE m.id = :id
			AND m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.TEMPORARY
			AND m.purpose = :purpose
	""")
    Optional<Media> findAttachableById(@Param("id") String id, @Param("purpose") MediaPurpose purpose);

    @Query("""
		SELECT m FROM Media m
		WHERE m.id = :id AND m.status = :status
	""")
    Optional<Media> findByIdAndStatus(@Param("id") String id, @Param("status") MediaStatus status);
}
