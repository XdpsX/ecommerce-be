package com.xdpsx.ecommerce.user.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;

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

import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.domain.UserAddress;

@SpringJUnitConfig(UserAddressPersistenceTest.PersistenceConfig.class)
class UserAddressPersistenceTest {
    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserAddressRepository addressRepository;

    @BeforeEach
    void clearData() {
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerScopedQueries_ShouldIsolateRowsAndAllowMultipleAddressesPerUser() {
        User alice = userRepository.saveAndFlush(user("alice@example.com"));
        User bob = userRepository.saveAndFlush(user("bob@example.com"));
        UserAddress aliceFirst = addressRepository.saveAndFlush(address(alice, "Alice first"));
        UserAddress aliceSecond = addressRepository.saveAndFlush(address(alice, "Alice second"));
        UserAddress bobAddress = addressRepository.saveAndFlush(address(bob, "Bob"));

        assertThat(addressRepository.findAllByUserIdOrderByIdAsc(alice.getId()))
                .extracting(UserAddress::getId)
                .containsExactly(aliceFirst.getId(), aliceSecond.getId());
        assertThat(addressRepository.findAllByUserIdOrderByIdAsc(bob.getId()))
                .extracting(UserAddress::getId)
                .containsExactly(bobAddress.getId());
        assertThat(addressRepository.findByIdAndUserId(aliceFirst.getId(), bob.getId()))
                .isEmpty();
    }

    private static User user(String email) {
        return User.builder()
                .name("Customer")
                .email(email)
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
    }

    private static UserAddress address(User user, String recipientName) {
        UserAddress address = UserAddress.builder().user(user).build();
        address.replaceDetails(recipientName, "+84901234567", "Street", "Ward", "District", "City", null);
        return address;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, UserAddressRepository.class})
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:user_address;DB_CLOSE_DELAY=-1;MODE=MySQL");
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
    }
}
