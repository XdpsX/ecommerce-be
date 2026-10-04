package com.xdpsx.ecommerce.user.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;

@SpringJUnitConfig(UserEmailPersistenceTest.PersistenceConfig.class)
class UserEmailPersistenceTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:user_email;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=10000");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.xdpsx.ecommerce.user.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> userRepository.deleteAll());
    }

    @Test
    void uniqueEmail_ShouldRejectConcurrentEquivalentInserts() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> insertUser("alice@example.com"));
            Future<Boolean> second = executor.submit(() -> insertUser("alice@example.com"));

            boolean firstSucceeded = getResult(first);
            boolean secondSucceeded = getResult(second);

            assertThat(firstSucceeded ^ secondSucceeded).isTrue();
            assertThat(userRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private boolean insertUser(String email) {
        try {
            transactionTemplate.executeWithoutResult(status -> userRepository.saveAndFlush(User.builder()
                    .name("Customer")
                    .email(email)
                    .password("encoded")
                    .authProvider(AuthProvider.LOCAL)
                    .role(Role.USER)
                    .build()));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean getResult(Future<Boolean> future) throws Exception {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new AssertionError(exception.getCause());
        }
    }
}
