package com.xdpsx.ecommerce.user.application;

import com.xdpsx.ecommerce.user.api.dto.UserProfile;

public interface UserService {
    UserProfile getUserByEmail(String email);
}
