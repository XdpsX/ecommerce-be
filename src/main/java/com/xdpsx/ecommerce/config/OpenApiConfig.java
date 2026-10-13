package com.xdpsx.ecommerce.config;

import java.lang.reflect.Method;
import java.util.List;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PreAuthorize;

import com.xdpsx.ecommerce.auth.api.AuthController;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {
    @Bean
    public GroupedOpenApi backofficeApi() {
        return GroupedOpenApi.builder()
                .group("backoffice")
                .displayName("Backoffice API")
                .pathsToMatch("/auth/**", "/admin/**", "/media/**", "/orders", "/orders/**")
                .addOpenApiMethodFilter(OpenApiConfig::isBackofficeMethod)
                .build();
    }

    @Bean
    public GroupedOpenApi storefrontApi() {
        return GroupedOpenApi.builder()
                .group("storefront")
                .displayName("Storefront API")
                .pathsToMatch(
                        "/auth/**",
                        "/cart",
                        "/cart/**",
                        "/checkout/**",
                        "/orders",
                        "/orders/**",
                        "/users/**",
                        "/brands/**",
                        "/categories/**",
                        "/products/**")
                .addOpenApiMethodFilter(method -> !isAdminOnly(method))
                .build();
    }

    @Bean
    public GroupedOpenApi integrationApi() {
        return GroupedOpenApi.builder()
                .group("integration")
                .displayName("Integration API")
                .pathsToMatch("/webhooks/**", "/payments/**")
                .build();
    }

    private static boolean isAdminOnly(Method method) {
        PreAuthorize methodAuthorization = AnnotationUtils.findAnnotation(method, PreAuthorize.class);
        PreAuthorize classAuthorization =
                AnnotationUtils.findAnnotation(method.getDeclaringClass(), PreAuthorize.class);
        String expression = methodAuthorization != null
                ? methodAuthorization.value()
                : classAuthorization == null ? "" : classAuthorization.value();
        return expression.contains("hasRole('ADMIN')") || expression.contains("hasRole(\"ADMIN\")");
    }

    private static boolean isBackofficeMethod(Method method) {
        return isAdminOnly(method) || isBackofficeAuthenticationMethod(method);
    }

    private static boolean isBackofficeAuthenticationMethod(Method method) {
        if (!method.getDeclaringClass().equals(AuthController.class)) {
            return false;
        }
        return switch (method.getName()) {
            case "login", "refresh", "logout" -> true;
            default -> false;
        };
    }

    @Bean
    public OpenAPI openAPI(
            @Value("${openapi.service.title}") String title,
            @Value("${openapi.service.version}") String version,
            @Value("${openapi.service.server}") String serverUrl,
            @Value("${openapi.service.description}") String description) {
        final String securitySchemeName = "Bearer Authorization";
        return new OpenAPI()
                .servers(List.of(new Server().url(serverUrl)))
                .components(new Components()
                        .addSecuritySchemes(
                                securitySchemeName,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .security(List.of(new SecurityRequirement().addList(securitySchemeName)))
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}
