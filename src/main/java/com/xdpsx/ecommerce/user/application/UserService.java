package com.xdpsx.ecommerce.user.application;

import com.xdpsx.ecommerce.user.api.dto.UpdateUserProfileRequest;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;

public interface UserService {
    UserProfile getUserByEmail(String email);

    UserProfile updateCurrentProfile(String email, UpdateUserProfileRequest request);
}
