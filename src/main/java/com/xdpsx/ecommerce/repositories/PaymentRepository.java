package com.xdpsx.ecommerce.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xdpsx.ecommerce.entities.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {}
