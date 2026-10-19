package com.xdpsx.ecommerce.media.application.attachment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

/** Enforces immutable Media ownership while the calling Catalog transaction is active. */
@Service
@RequiredArgsConstructor
public class MediaAttachmentService {
    private final MediaRepository mediaRepository;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Media> synchronizeAttachments(
            MediaOwner owner,
            MediaPurpose purpose,
            Collection<String> currentMediaIds,
            Collection<String> desiredMediaIds) {
        if (owner == null || purpose == null || !owner.type().supports(purpose)) {
            throw new IllegalArgumentException("Media owner and purpose are incompatible");
        }
        Set<String> current = normalizedIds(currentMediaIds);
        Set<String> desired = normalizedIds(desiredMediaIds);
        List<String> lockIds = new ArrayList<>(new LinkedHashSet<>(current));
        desired.stream().filter(id -> !current.contains(id)).forEach(lockIds::add);
        lockIds.sort(String::compareTo);
        if (lockIds.isEmpty()) return List.of();

        List<Media> locked = mediaRepository.findAllByIdInForUpdate(lockIds);
        // Spring Data may return an already-managed instance from the first-level cache. Refresh while the
        // pessimistic lock is held so a webhook commit made after an earlier Catalog read is not overwritten.
        locked.forEach(media -> entityManager.refresh(media, LockModeType.PESSIMISTIC_WRITE));
        Map<String, Media> byId = locked.stream().collect(Collectors.toMap(Media::getId, Function.identity()));
        if (byId.size() != lockIds.size()) {
            String missing = lockIds.stream()
                    .filter(id -> !byId.containsKey(id))
                    .findFirst()
                    .orElse("unknown");
            throw mediaNotFound(missing);
        }

        // Validate every relation before mutating any row. ACTIVE legacy rows without an owner fail closed.
        for (String id : current) {
            Media media = byId.get(id);
            if (media.getPurpose() != purpose || !media.retain(owner.type(), owner.id())) {
                throw attachmentConflict(id);
            }
        }
        Long uploaderId = currentUserIdProvider.getRequiredUserId();
        for (String id : desired) {
            if (current.contains(id)) continue;
            Media media = byId.get(id);
            if (media.getPurpose() != purpose
                    || media.getUploadStatus() != MediaUploadStatus.UPLOADED
                    || media.getAttachmentStatus() != MediaAttachmentStatus.TEMPORARY
                    || media.getAttachmentOwnerType() != null
                    || media.getAttachmentOwnerId() != null
                    || !uploaderId.equals(media.getUploadedByUserId())) {
                throw mediaNotFound(id);
            }
        }

        for (String id : current) {
            if (!desired.contains(id)) byId.get(id).detach(owner.type(), owner.id());
        }
        for (String id : desired) {
            if (!current.contains(id)) byId.get(id).attach(owner.type(), owner.id());
        }
        mediaRepository.saveAll(locked);
        return desired.stream().map(byId::get).toList();
    }

    private static Set<String> normalizedIds(Collection<String> ids) {
        if (ids == null) return Set.of();
        if (ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("Media IDs must not be blank");
        }
        return new LinkedHashSet<>(ids);
    }

    private static ApplicationException mediaNotFound(String id) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "media", "resourceId", id));
    }

    private static ApplicationException attachmentConflict(String id) {
        return new ApplicationException(
                ErrorCode.MEDIA_ATTACHMENT_CONFLICT, Map.of("resourceType", "media", "resourceId", id));
    }
}
