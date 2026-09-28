package com.xdpsx.ecommerce.catalog.variantoption.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOption;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;

/** Verifies dictionary ordering and database uniqueness with a real Hibernate persistence context. */
@SpringJUnitConfig(VariantOptionPersistenceTest.PersistenceConfig.class)
class VariantOptionPersistenceTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = {VariantOptionRepository.class, VariantOptionValueRepository.class})
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:variant_options;DB_CLOSE_DELAY=-1;MODE=MySQL");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.xdpsx.ecommerce.catalog.variantoption.domain");
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

    @Autowired
    private VariantOptionRepository optionRepository;

    @Autowired
    private VariantOptionValueRepository valueRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            valueRepository.deleteAll();
            optionRepository.deleteAll();
        });
    }

    @Test
    void readShouldKeepOptionsAndValuesInDisplayOrder() {
        transactionTemplate.executeWithoutResult(status -> {
            VariantOption color = optionRepository.saveAndFlush(option("color", "Color", 1));
            VariantOption size = optionRepository.saveAndFlush(option("size", "Size", 0));
            valueRepository.save(value(color, "white", "White", 1));
            valueRepository.save(value(color, "black", "Black", 0));
            valueRepository.save(value(size, "medium", "M", 1));
            valueRepository.saveAndFlush(value(size, "small", "S", 0));
        });

        List<VariantOption> options = transactionTemplate.execute(status -> optionRepository.findAllWithValues());

        assertThat(options).extracting(VariantOption::getCode).containsExactly("size", "color");
        assertThat(options.get(1).getValues())
                .extracting(VariantOptionValue::getCode)
                .containsExactly("black", "white");
    }

    @Test
    void uniqueConstraintsShouldProtectOptionCodeValueCodeAndDisplayOrder() {
        VariantOption color =
                transactionTemplate.execute(status -> optionRepository.saveAndFlush(option("color", "Color", 0)));
        transactionTemplate.executeWithoutResult(
                status -> valueRepository.saveAndFlush(value(color, "black", "Black", 0)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                        status -> optionRepository.saveAndFlush(option("color", "Other", 1))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    VariantOption managed =
                            optionRepository.findById(color.getId()).orElseThrow();
                    valueRepository.saveAndFlush(value(managed, "black", "Other", 1));
                }))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    VariantOption managed =
                            optionRepository.findById(color.getId()).orElseThrow();
                    valueRepository.saveAndFlush(value(managed, "white", "White", 0));
                }))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void reorderShouldPersistDirectSwapAfterTemporaryOrders() {
        VariantOption color =
                transactionTemplate.execute(status -> optionRepository.saveAndFlush(option("color", "Color", 0)));
        transactionTemplate.executeWithoutResult(status -> {
            valueRepository.save(value(color, "black", "Black", 0));
            valueRepository.saveAndFlush(value(color, "white", "White", 1));
        });

        transactionTemplate.executeWithoutResult(status -> {
            VariantOption managed =
                    optionRepository.findByIdWithValues(color.getId()).orElseThrow();
            managed.getValues().get(0).setDisplayOrder(-1);
            managed.getValues().get(1).setDisplayOrder(-2);
            valueRepository.flush();
            managed.getValues().get(0).setDisplayOrder(1);
            managed.getValues().get(1).setDisplayOrder(0);
            valueRepository.flush();
        });

        List<VariantOption> options = transactionTemplate.execute(status -> optionRepository.findAllWithValues());
        assertThat(options.get(0).getValues())
                .extracting(VariantOptionValue::getCode)
                .containsExactly("white", "black");
    }

    private static VariantOption option(String code, String name, int order) {
        return VariantOption.builder()
                .code(code)
                .name(name)
                .displayOrder(order)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }

    private static VariantOptionValue value(VariantOption option, String code, String name, int order) {
        return VariantOptionValue.builder()
                .option(option)
                .code(code)
                .name(name)
                .displayOrder(order)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }
}
