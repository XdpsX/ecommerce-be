package com.xdpsx.ecommerce.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xdpsx.ecommerce.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {}
