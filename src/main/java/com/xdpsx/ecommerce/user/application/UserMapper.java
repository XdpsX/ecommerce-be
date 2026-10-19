package com.xdpsx.ecommerce.user.application;

import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploader;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.User;

@Component
public class UserMapper {
    private final CloudinaryUploader uploader;

    public UserMapper(CloudinaryUploader uploader) {
        this.uploader = uploader;
    }

    public UserProfile fromEntityToProfile(User entity) {
        UserProfile response = UserProfile.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .build();
        if (entity.getAuthProvider().equals(AuthProvider.LOCAL)) {
            response.setAvatarUrl(uploader.getFileUrl(entity.getAvatar()));
        } else {
            response.setAvatarUrl(entity.getAvatar());
        }
        response.setRole(entity.getRole().name());
        return response;
    }
}
