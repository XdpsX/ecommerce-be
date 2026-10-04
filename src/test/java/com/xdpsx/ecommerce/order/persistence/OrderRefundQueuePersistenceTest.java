package com.xdpsx.ecommerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.refund.api.dto.RefundQueueItemResponse;
import com.xdpsx.ecommerce.refund.domain.Refund;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.refund.persistence.RefundRepository;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

/** Exercises the Order and Refund paging queries against MySQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(OrderRefundQueuePersistenceTest.PersistenceConfig.class)
class OrderRefundQueuePersistenceTest {
    private static final LocalDateTime SAME_CREATED_AT = LocalDateTime.parse("2026-10-01T12:00:00");
    private static final LocalDateTime SAME_REQUESTED_AT = LocalDateTime.parse("2026-10-02T12:00:00");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("order_refund_queue_persistence");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminOrderPage_ShouldUseCreatedAtAndIdTieBreakAcrossPages() {
        Order first = saveOrder("TRK-SAME-1", OrderStatus.CONFIRMED, PaymentStatus.PENDING, SAME_CREATED_AT);
        Order second = saveOrder("TRK-SAME-2", OrderStatus.CONFIRMED, PaymentStatus.PENDING, SAME_CREATED_AT);
        Order third = saveOrder("TRK-SAME-3", OrderStatus.CONFIRMED, PaymentStatus.PENDING, SAME_CREATED_AT);
        Order older =
                saveOrder("TRK-OLDER", OrderStatus.CONFIRMED, PaymentStatus.PENDING, SAME_CREATED_AT.minusDays(1));
        setCreatedAt(first, SAME_CREATED_AT);
        setCreatedAt(second, SAME_CREATED_AT);
        setCreatedAt(third, SAME_CREATED_AT);
        setCreatedAt(older, SAME_CREATED_AT.minusDays(1));
        Sort stableOrder = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<Order> firstPage = orderRepository.findAll(PageRequest.of(0, 2, stableOrder));
        Page<Order> secondPage = orderRepository.findAll(PageRequest.of(1, 2, stableOrder));

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(Order::getTrackingNumber)
                .containsExactly("TRK-SAME-3", "TRK-SAME-2");
        assertThat(secondPage.getContent())
                .extracting(Order::getTrackingNumber)
                .containsExactly("TRK-SAME-1", "TRK-OLDER");
    }

    @Test
    void customerOrderPage_ShouldUseUpdatedOrCreatedAtThenIdTieBreakAcrossPages() {
        User user = userRepository.saveAndFlush(User.builder()
                .name("Customer")
                .email("customer@example.test")
                .password("encoded")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
        Order older = saveOrder("TRK-CUSTOMER-OLDER", user, SAME_CREATED_AT.minusDays(1));
        Order first = saveOrder("TRK-CUSTOMER-SAME-1", user, SAME_CREATED_AT);
        Order second = saveOrder("TRK-CUSTOMER-SAME-2", user, SAME_CREATED_AT);
        setCustomerTimestamps(older, SAME_CREATED_AT.minusDays(3), null);
        setCustomerTimestamps(first, SAME_CREATED_AT.minusDays(1), SAME_CREATED_AT);
        setCustomerTimestamps(second, SAME_CREATED_AT.minusDays(2), SAME_CREATED_AT);

        Page<Order> firstPage = orderRepository.findByUser(user.getId(), PageRequest.of(0, 1));
        Page<Order> secondPage = orderRepository.findByUser(user.getId(), PageRequest.of(1, 1));
        Page<Order> thirdPage = orderRepository.findByUser(user.getId(), PageRequest.of(2, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(Order::getTrackingNumber).containsExactly("TRK-CUSTOMER-SAME-2");
        assertThat(secondPage.getContent()).extracting(Order::getTrackingNumber).containsExactly("TRK-CUSTOMER-SAME-1");
        assertThat(thirdPage.getContent()).extracting(Order::getTrackingNumber).containsExactly("TRK-CUSTOMER-OLDER");
    }

    @Test
    void adminOrderFilter_ShouldIntersectTrackingNumberAndBothStatuses() {
        Order matching = saveOrder("TRK-MATCH", OrderStatus.CONFIRMED, PaymentStatus.PENDING, SAME_CREATED_AT);
        saveOrder("TRK-OTHER-ORDER", OrderStatus.CANCELLED, PaymentStatus.PENDING, SAME_CREATED_AT);
        saveOrder("TRK-OTHER-PAYMENT", OrderStatus.CONFIRMED, PaymentStatus.PAID, SAME_CREATED_AT);
        var matches = orderRepository.findAll(
                OrderSpecification.withStatusAndPaymentStatus(
                        OrderStatus.CONFIRMED, PaymentStatus.PENDING, " TRK-MATCH "),
                PageRequest.of(0, 5, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        var wrongOrderStatus = orderRepository.findAll(
                OrderSpecification.withStatusAndPaymentStatus(
                        OrderStatus.CANCELLED, PaymentStatus.PENDING, "TRK-MATCH"),
                PageRequest.of(0, 5));

        assertThat(matches.getTotalElements()).isEqualTo(1);
        assertThat(matches.getContent()).extracting(Order::getId).containsExactly(matching.getId());
        assertThat(wrongOrderStatus).isEmpty();
    }

    @Test
    void refundQueue_ShouldFilterAndPageStableProjectionWithAccurateCount() {
        saveRefund("TRK-REFUND-1", RefundStatus.PENDING, SAME_REQUESTED_AT);
        saveRefund("TRK-REFUND-2", RefundStatus.PENDING, SAME_REQUESTED_AT);
        saveRefund("TRK-REFUND-FAILED", RefundStatus.FAILED, SAME_REQUESTED_AT.minusDays(1));
        saveRefund("TRK-REFUND-3", RefundStatus.PENDING, SAME_REQUESTED_AT.plusDays(1));
        Page<RefundQueueItemResponse> allFirstPage = refundRepository.findQueue(null, PageRequest.of(0, 2));
        Page<RefundQueueItemResponse> allSecondPage = refundRepository.findQueue(null, PageRequest.of(1, 2));
        Page<RefundQueueItemResponse> pendingFirstPage =
                refundRepository.findQueue(RefundStatus.PENDING, PageRequest.of(0, 2));
        Page<RefundQueueItemResponse> pendingSecondPage =
                refundRepository.findQueue(RefundStatus.PENDING, PageRequest.of(1, 2));

        assertThat(allFirstPage.getTotalElements()).isEqualTo(4);
        assertThat(allFirstPage.getTotalPages()).isEqualTo(2);
        assertThat(allFirstPage.getContent())
                .extracting(RefundQueueItemResponse::trackingNumber)
                .containsExactly("TRK-REFUND-FAILED", "TRK-REFUND-1");
        assertThat(allSecondPage.getContent())
                .extracting(RefundQueueItemResponse::trackingNumber)
                .containsExactly("TRK-REFUND-2", "TRK-REFUND-3");
        assertThat(allFirstPage.getContent().get(0).status()).isEqualTo(RefundStatus.FAILED);
        assertThat(allFirstPage.getContent().get(0).orderId()).isNotNull();
        assertThat(allFirstPage.getContent().get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(allFirstPage.getContent().get(0).currency()).isEqualTo("VND");
        assertThat(allFirstPage.getContent().get(0).requestedAt()).isEqualTo(SAME_REQUESTED_AT.minusDays(1));

        assertThat(pendingFirstPage.getTotalElements()).isEqualTo(3);
        assertThat(pendingFirstPage.getTotalPages()).isEqualTo(2);
        assertThat(pendingFirstPage.getContent())
                .extracting(RefundQueueItemResponse::trackingNumber)
                .containsExactly("TRK-REFUND-1", "TRK-REFUND-2");
        assertThat(pendingSecondPage.getContent())
                .extracting(RefundQueueItemResponse::trackingNumber)
                .containsExactly("TRK-REFUND-3");
    }

    private Order saveOrder(
            String trackingNumber, OrderStatus orderStatus, PaymentStatus paymentStatus, LocalDateTime createdAt) {
        return orderRepository.saveAndFlush(order(trackingNumber, null, orderStatus, paymentStatus, createdAt));
    }

    private Order saveOrder(String trackingNumber, User user, LocalDateTime createdAt) {
        return orderRepository.saveAndFlush(order(trackingNumber, user, OrderStatus.CONFIRMED, null, createdAt));
    }

    private void setCreatedAt(Order order, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), order.getId());
    }

    private void setCustomerTimestamps(Order order, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (updatedAt == null) {
            jdbcTemplate.update(
                    "UPDATE orders SET created_at = ?, updated_at = NULL WHERE id = ?",
                    Timestamp.valueOf(createdAt),
                    order.getId());
        } else {
            jdbcTemplate.update(
                    "UPDATE orders SET created_at = ?, updated_at = ? WHERE id = ?",
                    Timestamp.valueOf(createdAt),
                    Timestamp.valueOf(updatedAt),
                    order.getId());
        }
    }

    private static Order order(
            String trackingNumber,
            User user,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            LocalDateTime createdAt) {
        Order order = Order.builder()
                .trackingNumber(trackingNumber)
                .status(orderStatus)
                .user(user)
                .address("1 Main Street")
                .mobileNumber("0123456789")
                .totalAmount(new BigDecimal("100.00"))
                .currency("VND")
                .createdAt(createdAt)
                .build();
        if (paymentStatus != null) {
            order.setPayment(
                    Payment.builder().order(order).status(paymentStatus).build());
        }
        return order;
    }

    private Refund saveRefund(String trackingNumber, RefundStatus status, LocalDateTime requestedAt) {
        Order order = saveOrder(trackingNumber, OrderStatus.CANCELLED, PaymentStatus.PAID, SAME_CREATED_AT);
        return refundRepository.saveAndFlush(Refund.builder()
                .payment(order.getPayment())
                .status(status)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .reason("Customer cancellation")
                .requestedBy("payment-callback")
                .requestedAt(requestedAt)
                .build());
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(
            basePackageClasses = {
                OrderRepository.class,
                PaymentRepository.class,
                RefundRepository.class,
                UserRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DataSource dataSource() {
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(MYSQL.getJdbcUrl());
            config.setUsername(MYSQL.getUsername());
            config.setPassword(MYSQL.getPassword());
            config.setMaximumPoolSize(4);
            return new com.zaxxer.hikari.HikariDataSource(config);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
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
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "update");
            factory.getJpaPropertyMap()
                    .put(
                            "hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            factory.getJpaPropertyMap().put("hibernate.jdbc.time_zone", "UTC");
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
