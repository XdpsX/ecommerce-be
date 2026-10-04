package com.xdpsx.ecommerce.user.application;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.user.api.dto.UpdateUserProfileRequest;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserProfile getUserByEmail(String email) {
        User user = findCurrentUser(email);
        return userMapper.fromEntityToProfile(user);
    }

    @Override
    @Transactional
    public UserProfile updateCurrentProfile(String email, UpdateUserProfileRequest request) {
        String normalizedName = normalizeName(request == null ? null : request.name());
        User user = findCurrentUser(email);
        user.rename(normalizedName);
        return userMapper.fromEntityToProfile(user);
    }

    private User findCurrentUser(String email) {
        return userRepository
                .findByEmail(EmailIdentity.canonicalize(email))
                .orElseThrow(
                        () -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user")));
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "name"));
        }
        String normalized = name.trim();
        if (normalized.length() > 64) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "name"));
        }
        return normalized;
    }
}
