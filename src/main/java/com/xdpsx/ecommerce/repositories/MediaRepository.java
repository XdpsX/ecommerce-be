package com.xdpsx.ecommerce.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.entities.Media;
import com.xdpsx.ecommerce.entities.enums.MediaResourceType;

public interface MediaRepository extends CrudRepository<Media, String> {
    @Query("SELECT m FROM Media m WHERE m.deleteFlg = true")
    List<Media> findDeletedMedia();

    @Query("""
		SELECT m FROM Media m
		WHERE m.tempFlg = true AND m.createdAt < :expiryTime
		""")
    List<Media> findExpiredMedia(@Param("expiryTime") LocalDateTime expiryTime);

    @Query("""
		SELECT m FROM Media m
		WHERE m.id = :id AND m.deleteFlg = false
	""")
    Optional<Media> findPublicMediaById(String id);

    @Query(
            """
		SELECT m FROM Media m
		WHERE m.id = :id AND m.deleteFlg = false
			AND m.tempFlg = true
			AND m.resourceType = :resourceType
	""")
    Optional<Media> findPublicTempMediaById(String id, MediaResourceType resourceType);
}
