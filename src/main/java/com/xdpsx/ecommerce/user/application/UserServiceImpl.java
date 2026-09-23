package com.xdpsx.ecommerce.user.application;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.common.error.NotFoundException;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;
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
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User with email=%s not found".formatted(email)));
        return userMapper.fromEntityToProfile(user);
    }
}
