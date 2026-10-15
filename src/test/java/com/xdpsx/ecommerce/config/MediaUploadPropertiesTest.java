package com.xdpsx.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class MediaUploadPropertiesTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(MediaUploadConfiguration.class);

    @Test
    void authorizationLifetime_ShouldRejectValuesShorterThanCloudinarySignatureWindow() {
        contextRunner
                .withPropertyValues("media.upload.authorization-lifetime=59m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void authorizationLifetime_ShouldRejectValuesLongerThanCloudinarySignatureWindow() {
        contextRunner
                .withPropertyValues("media.upload.authorization-lifetime=61m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void defaultAuthorizationLifetime_ShouldMatchCloudinaryOneHourWindow() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .getBean(MediaUploadProperties.class)
                .extracting(MediaUploadProperties::getAuthorizationLifetime)
                .isEqualTo(java.time.Duration.ofHours(1)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MediaUploadProperties.class)
    static class MediaUploadConfiguration {}
}
