package com.xdpsx.ecommerce.user.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploader;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;

@Mapper(componentModel = "spring")
public abstract class UserMapper {
    @Autowired
    private CloudinaryUploader uploader;

    @Mapping(target = "role", ignore = true)
    abstract UserProfile buildUserProfile(User entity);

    public UserProfile fromEntityToProfile(User entity) {
        UserProfile response = buildUserProfile(entity);
        if (entity.getAuthProvider().equals(AuthProvider.LOCAL)) {
            response.setAvatarUrl(uploader.getFileUrl(entity.getAvatar()));
        } else {
            response.setAvatarUrl(entity.getAvatar());
        }
        response.setRole(entity.getRole().name());
        return response;
    }
}
