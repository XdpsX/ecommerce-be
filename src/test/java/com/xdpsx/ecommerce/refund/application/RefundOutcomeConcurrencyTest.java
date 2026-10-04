package com.xdpsx.ecommerce.refund.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.refund.api.dto.RefundCompleteRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundFailRequest;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(RefundOutcomeConcurrencyTest.PersistenceConfig.class)
class RefundOutcomeConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("refund_outcome_concurrency");

    @org.springframework.beans.factory.annotation.Autowired
    private RefundService refundService;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OrderRepository orderRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private RefundRepository refundRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void completeAndFailConcurrently_ShouldPersistOnlyOneTerminalOutcome() throws Exception {
        Refund refund = savePendingRefund();
        CyclicBarrier start = new CyclicBarrier(2);

        Future<Outcome> complete = executor.submit(() -> completeAfter(start, refund.getId()));
        Future<Outcome> fail = executor.submit(() -> failAfter(start, refund.getId()));
        List<Outcome> outcomes = List.of(complete.get(10, TimeUnit.SECONDS), fail.get(10, TimeUnit.SECONDS));

        assertThat(outcomes).containsExactlyInAnyOrder(Outcome.SUCCEEDED, Outcome.REJECTED);
        Refund persisted = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isIn(RefundStatus.SUCCEEDED, RefundStatus.FAILED);
    }

    private Outcome completeAfter(CyclicBarrier start, Long refundId) {
        await(start);
        try {
            refundService.complete(refundId, new RefundCompleteRequest("provider-ref"), "admin@example.test");
            return Outcome.SUCCEEDED;
        } catch (RuntimeException exception) {
            return expectedTransitionRejection(exception);
        }
    }

    private Outcome failAfter(CyclicBarrier start, Long refundId) {
        await(start);
        try {
            refundService.fail(refundId, new RefundFailRequest("Provider unavailable"), "admin@example.test");
            return Outcome.SUCCEEDED;
        } catch (RuntimeException exception) {
            return expectedTransitionRejection(exception);
        }
    }

    private static Outcome expectedTransitionRejection(RuntimeException exception) {
        if (exception instanceof ApplicationException applicationException
                && applicationException.getCode() == ErrorCode.MALFORMED_REQUEST
                && "invalidRefundTransition"
                        .equals(applicationException.getParameters().get("reason"))) {
            return Outcome.REJECTED;
        }
        throw exception;
    }

    private Refund savePendingRefund() {
        User user = userRepository.saveAndFlush(User.builder()
                .name("Admin refund race")
                .email("refund-race-" + UUID.randomUUID() + "@example.test")
                .password("encoded")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
        Order order = Order.builder()
                .trackingNumber("TRK-" + UUID.randomUUID())
                .status(OrderStatus.CANCELLED)
                .user(user)
                .address("1 Main Street")
                .mobileNumber("0123456789")
                .totalAmount(new BigDecimal("100.00"))
                .currency("VND")
                .createdAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
        Payment payment =
                Payment.builder().order(order).status(PaymentStatus.PAID).build();
        order.setPayment(payment);
        order = orderRepository.saveAndFlush(order);
        Refund refund = Refund.builder()
                .payment(order.getPayment())
                .status(RefundStatus.PENDING)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .reason("Customer cancellation")
                .requestedBy("payment-callback")
                .requestedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
        return refundRepository.saveAndFlush(refund);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not synchronize refund callers", exception);
        }
    }

    private enum Outcome {
        SUCCEEDED,
        REJECTED
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(
            basePackageClasses = {
                UserRepository.class,
                OrderRepository.class,
                PaymentRepository.class,
                RefundRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(MYSQL.getJdbcUrl());
            dataSource.setUsername(MYSQL.getUsername());
            dataSource.setPassword(MYSQL.getPassword());
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.user.domain",
                    "com.xdpsx.ecommerce.order.domain",
                    "com.xdpsx.ecommerce.payment.domain",
                    "com.xdpsx.ecommerce.refund.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            factory.getJpaPropertyMap().put("hibernate.jdbc.time_zone", "UTC");
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RefundService refundService(
                RefundRepository refundRepository,
                OrderRepository orderRepository,
                PaymentRepository paymentRepository,
                Clock clock,
                jakarta.persistence.EntityManager entityManager) {
            return new RefundService(refundRepository, orderRepository, paymentRepository, clock, entityManager);
        }
    }
}
