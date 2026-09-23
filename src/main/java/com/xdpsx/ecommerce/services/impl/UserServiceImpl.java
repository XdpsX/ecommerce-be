package com.xdpsx.ecommerce.services.impl;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.dtos.user.UserProfile;
import com.xdpsx.ecommerce.entities.User;
import com.xdpsx.ecommerce.exceptions.NotFoundException;
import com.xdpsx.ecommerce.mappers.UserMapper;
import com.xdpsx.ecommerce.repositories.UserRepository;
import com.xdpsx.ecommerce.services.UserService;

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
