package com.xdpsx.ecommerce.auth.application;

@FunctionalInterface
public interface CurrentUserIdProvider {
    Long getRequiredUserId();
}
