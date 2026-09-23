package com.xdpsx.ecommerce.dtos.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfile {
    private Long id;
    private String name;
    private String email;
    private String avatarUrl;
    private String role;
}
