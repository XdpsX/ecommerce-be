package com.xdpsx.ecommerce.user.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xdpsx.ecommerce.user.domain.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findAllByUserIdOrderByIdAsc(Long userId);

    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);
}
