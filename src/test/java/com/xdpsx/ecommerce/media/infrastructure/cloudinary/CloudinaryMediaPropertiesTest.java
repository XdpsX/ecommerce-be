package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CloudinaryMediaPropertiesTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(CloudinaryPropertiesConfiguration.class);

    @Test
    void blankWebhookUrl_ShouldFailApplicationContextAtConfigurationBinding() {
        contextRunner
                .withPropertyValues("media.cloudinary.eager-notification-url= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void httpWebhookUrl_ShouldFailApplicationContextAtConfigurationBinding() {
        contextRunner
                .withPropertyValues(
                        "media.cloudinary.eager-notification-url=http://tunnel.example/webhooks/cloudinary/eager")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void httpsWebhookUrl_ShouldStartApplicationContextSuccessfully() {
        contextRunner
                .withPropertyValues(
                        "media.cloudinary.eager-notification-url=https://tunnel.example/webhooks/cloudinary/eager")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(CloudinaryMediaProperties.class)
                        .extracting(CloudinaryMediaProperties::getEagerNotificationUrl)
                        .isEqualTo("https://tunnel.example/webhooks/cloudinary/eager"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CloudinaryMediaProperties.class)
    static class CloudinaryPropertiesConfiguration {}
}
