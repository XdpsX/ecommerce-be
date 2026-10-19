package com.xdpsx.ecommerce.media.application.upload;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
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

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@SpringJUnitConfig(MediaUploadIntentTransactionTest.PersistenceConfig.class)
class MediaUploadIntentTransactionTest {
    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = MediaRepository.class)
    static class PersistenceConfig {
        @Bean
        javax.sql.DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:media_intent_tx;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(javax.sql.DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            factory.getJpaPropertyMap()
                    .put(
                            "hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
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

        @Bean
        CurrentUserIdProvider currentUserIdProvider() {
            return () -> 42L;
        }

        @Bean
        MediaUploadProperties mediaUploadProperties() {
            return new MediaUploadProperties();
        }

        @Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }

        @Bean
        MediaUploadIntentService mediaUploadIntentService(
                MediaUploadIntentPersistence persistence,
                MediaUploadAuthorizer authorizer,
                CurrentUserIdProvider userIdProvider,
                MediaUploadProperties properties,
                java.time.Clock clock) {
            return new MediaUploadIntentService(persistence, authorizer, userIdProvider, properties, clock);
        }

        @Bean
        MediaUploadIntentPersistence mediaUploadIntentPersistence(MediaRepository repository) {
            return new MediaUploadIntentPersistence(repository);
        }

        @Bean
        MediaUploadAuthorizer mediaUploadAuthorizer(java.time.Clock clock) {
            return request -> {
                java.time.LocalDateTime issued = java.time.LocalDateTime.ofInstant(
                                clock.instant(), java.time.ZoneOffset.UTC)
                        .withNano(0);
                return new MediaUploadAuthorizer.UploadAuthorization(
                        "https://api.cloudinary.com/v1_1/test/image/upload",
                        java.util.Map.of("signature", "test"),
                        issued,
                        issued.plusHours(1));
            };
        }
    }

    @Autowired
    private MediaUploadIntentService service;

    @Autowired
    private MediaRepository repository;

    @Test
    void create_ShouldPersistIntentBeforeReturningUploadAuthorization() {
        UploadIntentView result = service.create(
                new CreateUploadIntentCommand(MediaPurpose.PRODUCT_IMAGE, "image.png", "image/png", 1024));

        assertThat(repository.findById(result.mediaId()))
                .get()
                .extracting(Media::getUploadedByUserId)
                .isEqualTo(42L);
        assertThat(repository.findById(result.mediaId()).orElseThrow().getUploadAuthorizationIssuedAt())
                .isNotNull();
    }
}
