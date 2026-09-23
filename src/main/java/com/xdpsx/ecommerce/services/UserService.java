package com.xdpsx.ecommerce.services;

import com.xdpsx.ecommerce.dtos.user.UserProfile;

public interface UserService {
    UserProfile getUserByEmail(String email);
}
