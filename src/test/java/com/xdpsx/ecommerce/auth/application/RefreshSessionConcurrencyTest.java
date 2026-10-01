package com.xdpsx.ecommerce.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.auth.domain.RefreshSession;
import com.xdpsx.ecommerce.auth.infrastructure.security.TokenProvider;
import com.xdpsx.ecommerce.auth.persistence.RefreshSessionRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.AuthSessionProperties;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@Testcontainers
@SpringJUnitConfig(RefreshSessionConcurrencyTest.PersistenceConfig.class)
@TestPropertySource(
        properties = {"app.jwt.secret=test-secret-key-that-is-long-enough-for-hs256", "app.jwt.expiration.seconds=3600"
        })
class RefreshSessionConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("refresh_session_concurrency");

    @org.springframework.beans.factory.annotation.Autowired
    private RefreshSessionManager manager;

    @org.springframework.beans.factory.annotation.Autowired
    private AuthService authService;

    @org.springframework.beans.factory.annotation.Autowired
    private RefreshSessionRepository sessionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private TokenProvider tokenProvider;

    @org.springframework.beans.factory.annotation.Autowired
    private RefreshCredentialService credentialService;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void concurrentRotation_ShouldAllowOneSuccessThenRevokeOnReuse() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .name("Customer")
                .email("customer@example.com")
                .password("encoded")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
        when(tokenProvider.generateLocalToken(any(User.class))).thenReturn("access-token");
        AuthenticatedSession issued = manager.issue(user);
        CyclicBarrier start = new CyclicBarrier(2);

        Future<RefreshRotationOutcome> first = executor.submit(() -> rotateAfter(start, issued.refreshCredential()));
        Future<RefreshRotationOutcome> second = executor.submit(() -> rotateAfter(start, issued.refreshCredential()));

        List<RefreshRotationOutcome> outcomes =
                List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(outcomes.stream().filter(RefreshRotationOutcome::successful).count())
                .isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).count())
                .isEqualTo(1);
        RefreshSession persisted = sessionRepository
                .findById(issued.refreshCredential().substring(0, 36))
                .orElseThrow();
        assertThat(persisted.getRevokedAt()).isNotNull();
    }

    @Test
    void refreshThroughAuthService_ShouldCommitReuseRevocationBeforeReturningError() {
        User user = saveUser("reuse-boundary@example.com");
        when(tokenProvider.generateLocalToken(any(User.class))).thenReturn("access-token");
        AuthenticatedSession issued = manager.issue(user);

        authService.refresh(issued.refreshCredential());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.refresh(issued.refreshCredential()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_CREDENTIAL));

        RefreshSession persisted = sessionRepository
                .findById(issued.refreshCredential().substring(0, 36))
                .orElseThrow();
        assertThat(persisted.getRevokedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "{0} credentials are rejected")
    @MethodSource("invalidCredentialScenarios")
    void refreshThroughAuthService_ShouldRejectInvalidCredentialStates(String scenario) {
        User user = saveUser(scenario + "-credential@example.com");
        String credential;
        if ("malformed".equals(scenario)) {
            credential = "not-a-refresh-credential";
        } else {
            RefreshCredentialService.IssuedCredential issued =
                    credentialService.issue(UUID.randomUUID().toString());
            credential = issued.rawCredential();
            if ("expired".equals(scenario) || "revoked".equals(scenario)) {
                RefreshSession session = RefreshSession.builder()
                        .id(credential.substring(0, 36))
                        .user(user)
                        .secretHash(issued.secretHash())
                        .createdAt(NOW.minus(Duration.ofDays(1)))
                        .expiresAt("expired".equals(scenario) ? NOW.minusSeconds(1) : NOW.plus(Duration.ofDays(1)))
                        .revokedAt("revoked".equals(scenario) ? NOW.minusSeconds(1) : null)
                        .build();
                sessionRepository.saveAndFlush(session);
            }
        }

        String invalidCredential = credential;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.refresh(invalidCredential))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_CREDENTIAL));
    }

    private static Stream<Arguments> invalidCredentialScenarios() {
        return Stream.of(
                Arguments.of("malformed"), Arguments.of("unknown"), Arguments.of("expired"), Arguments.of("revoked"));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .name("Customer")
                .email(email)
                .password("encoded")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
    }

    private RefreshRotationOutcome rotateAfter(CyclicBarrier start, String credential) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return manager.rotate(credential);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {RefreshSessionRepository.class, UserRepository.class})
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
            factory.setPackagesToScan("com.xdpsx.ecommerce.auth.domain", "com.xdpsx.ecommerce.user.domain");
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
        AuthSessionProperties authSessionProperties() {
            AuthSessionProperties properties = new AuthSessionProperties();
            properties.setRefreshSessionLifetime(Duration.ofDays(30));
            return properties;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RefreshCredentialService refreshCredentialService() {
            return new RefreshCredentialService();
        }

        @Bean
        TokenProvider tokenProvider() {
            return mock(TokenProvider.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        AuthenticationManager authenticationManager() {
            return mock(AuthenticationManager.class);
        }

        @Bean
        RefreshSessionManager refreshSessionManager(
                RefreshSessionRepository repository,
                RefreshCredentialService credentialService,
                TokenProvider tokenProvider,
                AuthSessionProperties properties,
                Clock clock) {
            return new RefreshSessionManager(repository, credentialService, tokenProvider, properties, clock);
        }

        @Bean
        AuthService authService(
                UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                AuthenticationManager authenticationManager,
                RefreshSessionManager refreshSessionManager) {
            return new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager, refreshSessionManager);
        }
    }
}
