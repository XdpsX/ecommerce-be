package com.xdpsx.ecommerce.user.application;

import com.xdpsx.ecommerce.user.api.dto.UserProfile;
import com.xdpsx.ecommerce.user.domain.User;

public interface UserService {
    UserProfile getUserByEmail(String email);
}
