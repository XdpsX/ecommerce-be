package com.xdpsx.ecommerce.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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

import com.xdpsx.ecommerce.config.PaymentAttemptProperties;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(PaymentAttemptConcurrencyTest.PersistenceConfig.class)
class PaymentAttemptConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("payment_attempt_concurrency");

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentAttemptPreparationService preparationService;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OrderRepository orderRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        paymentAttemptRepository.deleteAll();
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
    void concurrentRetry_ShouldCreateOneActiveAttemptAndReuseIt() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .name("Customer")
                .email("customer-" + UUID.randomUUID() + "@example.test")
                .password("encoded")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
        Order order = savePendingOrder(user);
        CyclicBarrier start = new CyclicBarrier(2);

        Future<PreparedPaymentAttempt> first =
                executor.submit(() -> prepareAfter(start, user.getEmail(), order.getId()));
        Future<PreparedPaymentAttempt> second =
                executor.submit(() -> prepareAfter(start, user.getEmail(), order.getId()));

        List<PreparedPaymentAttempt> attempts =
                List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(attempts)
                .extracting(PreparedPaymentAttempt::providerReference)
                .containsOnly(attempts.get(0).providerReference());
        assertThat(paymentAttemptRepository.count()).isEqualTo(1);
    }

    private PreparedPaymentAttempt prepareAfter(CyclicBarrier start, String email, Long orderId) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return preparationService.prepare(email, orderId);
    }

    private Order savePendingOrder(User user) {
        Order order = Order.builder()
                .trackingNumber("TRK-" + UUID.randomUUID())
                .status(OrderStatus.PENDING_PAYMENT)
                .user(user)
                .address("1 Main Street")
                .mobileNumber("0123456789")
                .totalAmount(new BigDecimal("100.00"))
                .currency("VND")
                .reservationExpiresAt(NOW.plus(Duration.ofMinutes(30)))
                .createdAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
        Payment payment =
                Payment.builder().order(order).status(PaymentStatus.PENDING).build();
        order.setPayment(payment);
        return orderRepository.saveAndFlush(order);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(
            basePackageClasses = {
                UserRepository.class,
                OrderRepository.class,
                PaymentRepository.class,
                PaymentAttemptRepository.class
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
        PaymentAttemptProperties paymentAttemptProperties() {
            PaymentAttemptProperties properties = new PaymentAttemptProperties();
            properties.setLifetime(Duration.ofMinutes(15));
            return properties;
        }

        @Bean
        PaymentAttemptPreparationService paymentAttemptPreparationService(
                UserRepository userRepository,
                OrderRepository orderRepository,
                PaymentAttemptRepository paymentAttemptRepository,
                PaymentAttemptProperties properties,
                Clock clock) {
            return new PaymentAttemptPreparationService(
                    userRepository, orderRepository, paymentAttemptRepository, properties, clock);
        }
    }
}
