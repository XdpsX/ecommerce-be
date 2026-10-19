package com.xdpsx.ecommerce.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.xdpsx.ecommerce.EcommerceApplication;
import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector.InspectedMediaAsset;
import com.xdpsx.ecommerce.media.application.MediaUploadAuthorizer;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerNotification;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryMediaTransformations;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.NotificationSignatureVerifier;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exercises the real HTTP, application, persistence, webhook, and Catalog attachment flow on MySQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MediaDirectUploadIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MediaDirectUploadIntegrationTest {
    private static final String ADMIN = "admin@example.test";
    private static final long UPLOADER_ID = 42L;

    @Configuration
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "com.xdpsx.ecommerce")
    @ConfigurationPropertiesScan("com.xdpsx.ecommerce")
    @ComponentScan(
            basePackages = "com.xdpsx.ecommerce",
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.xdpsx\\.ecommerce\\..*Test.*"),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {EcommerceApplication.class, SecurityConfigForControllerTests.class})
            })
    static class TestApplication {}

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("media_direct_upload_integration");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("CLOUDINARY_API_KEY", () -> "test-api-key");
        registry.add("CLOUDINARY_API_SECRET", () -> "test-api-secret");
        registry.add("CLOUDINARY_CLOUD_NAME", () -> "test-cloud");
        registry.add("CLOUDINARY_EAGER_NOTIFICATION_URL", () -> "https://example.test/webhooks/cloudinary/eager");
        registry.add("JWT_SECRET", () -> "integration-test-secret-that-is-long-enough");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-client");
        registry.add("GOOGLE_CLIENT_SECRET", () -> "test-secret");
        registry.add("VNPAY_TMN_CODE", () -> "test-tmn");
        registry.add("VNPAY_SECRET_KEY", () -> "test-vnpay-secret");
        registry.add("media.reconciliation.enabled", () -> "false");
        registry.add("media.cleanup.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CurrentUserIdProvider currentUserIdProvider;

    @MockitoBean
    private MediaUploadAuthorizer uploadAuthorizer;

    @MockitoBean
    private MediaAssetInspector assetInspector;

    @MockitoBean
    private NotificationSignatureVerifier signatureVerifier;

    @BeforeEach
    void configureBoundaries() {
        reset(currentUserIdProvider, uploadAuthorizer, assetInspector, signatureVerifier);
        when(currentUserIdProvider.getRequiredUserId()).thenReturn(UPLOADER_ID);
        when(uploadAuthorizer.authorize(any())).thenAnswer(invocation -> {
            MediaUploadAuthorizer.UploadAuthorizationRequest request = invocation.getArgument(0);
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
            return new MediaUploadAuthorizer.UploadAuthorization(
                    "https://api.cloudinary.com/v1_1/test-cloud/image/upload",
                    Map.of("api_key", "test-key", "public_id", request.publicId(), "signature", "test-signature"),
                    now,
                    now.plusHours(1));
        });
        when(assetInspector.inspect(anyString())).thenAnswer(invocation -> inspectedAsset(invocation.getArgument(0)));
        when(signatureVerifier.isValid(any(), any(), any())).thenReturn(true);
    }

    @Test
    void completeProcessingThenCatalogAttachThenWebhookReady_shouldKeepUploadAndProcessingIndependent()
            throws Exception {
        String mediaId = createIntent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media WHERE id = ?", Integer.class, mediaId))
                .isEqualTo(1);

        mockMvc.perform(post("/media/{id}/complete", mediaId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.processingStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.originalUrl").value("https://res.cloudinary.com/test/image/upload/original.png"))
                .andExpect(jsonPath("$.variants").isEmpty());

        String brandJson = objectMapper.writeValueAsString(Map.of(
                "name",
                "Direct upload integration " + mediaId,
                "status",
                "ACTIVE",
                "imageId",
                mediaId,
                "categoryIds",
                List.of()));
        mockMvc.perform(post("/admin/brands")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandJson))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT attachment_status, attachment_owner_type FROM media WHERE id = ?", mediaId))
                .containsEntry("attachment_status", "ACTIVE")
                .containsEntry("attachment_owner_type", "BRAND");

        postWebhook(mediaId, "ready");
        mockMvc.perform(get("/media/{id}", mediaId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.processingStatus").value("READY"))
                .andExpect(jsonPath("$.attachmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.variants.productCard").doesNotExist());
        // Brand variants are represented by brandLogo; a webhook must not require a second Catalog write.
        mockMvc.perform(get("/media/{id}", mediaId).with(admin()))
                .andExpect(jsonPath("$.variants.brandLogo").isNotEmpty());

        when(currentUserIdProvider.getRequiredUserId()).thenReturn(99L);
        mockMvc.perform(get("/media/{id}", mediaId).with(admin())).andExpect(status().isNotFound());
        verify(assetInspector).inspect("brand/" + mediaId);
    }

    @Test
    void earlyReadyWebhook_shouldNotReplaceBackendUploadVerification() throws Exception {
        String mediaId = createIntent();
        postWebhook(mediaId, "ready");

        mockMvc.perform(post("/media/{id}/complete", mediaId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.processingStatus").value("READY"));
        mockMvc.perform(post("/media/{id}/complete", mediaId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("READY"));
        verify(assetInspector).inspect("brand/" + mediaId);
    }

    @Test
    void crossUploaderCompleteStatusAndDelete_shouldReturnNotFoundWithoutProviderWorkOrMutation() throws Exception {
        String mediaId = createIntent();
        when(currentUserIdProvider.getRequiredUserId()).thenReturn(99L);

        mockMvc.perform(post("/media/{id}/complete", mediaId).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/{id}", mediaId).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/media/{id}", mediaId).with(admin())).andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForMap("SELECT upload_status, attachment_status FROM media WHERE id = ?", mediaId))
                .containsEntry("upload_status", "PENDING_UPLOAD")
                .containsEntry("attachment_status", "TEMPORARY");
        verifyNoInteractions(assetInspector);
    }

    private String createIntent() throws Exception {
        MvcResult result = mockMvc.perform(post("/media/upload-intents")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"BRAND_LOGO","filename":"logo.png","contentType":"image/png","sizeBytes":2048}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upload.fields.signature").value("test-signature"))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("mediaId").asString();
    }

    private void postWebhook(String mediaId, String terminalStatus) throws Exception {
        String publicId = "brand/" + mediaId;
        List<CloudinaryEagerNotification.EagerResult> eager = terminalStatus.equals("ready")
                ? CloudinaryMediaTransformations.eagerStringsFor(MediaPurpose.BRAND_LOGO).stream()
                        .map(transformation -> new CloudinaryEagerNotification.EagerResult(
                                transformation,
                                "https://res.cloudinary.com/test/image/upload/brand-logo.png",
                                "success",
                                null,
                                null,
                                null,
                                "png",
                                512L,
                                280,
                                280))
                        .toList()
                : List.of();
        CloudinaryEagerNotification notification = new CloudinaryEagerNotification(
                "eager",
                "batch-" + mediaId,
                publicId,
                "asset-" + mediaId,
                java.time.Instant.now().toString(),
                terminalStatus.equals("ready") ? "success" : "failed",
                null,
                null,
                null,
                null,
                eager);
        mockMvc.perform(post(CloudinaryWebhookController.EAGER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(notification)))
                .andExpect(status().isOk());
    }

    private static InspectedMediaAsset inspectedAsset(String publicId) {
        String mediaId = publicId.substring(publicId.indexOf('/') + 1);
        return new InspectedMediaAsset(
                publicId,
                "asset-" + mediaId,
                "image",
                "upload",
                "png",
                "https://res.cloudinary.com/test/image/upload/original.png",
                2048,
                560,
                560,
                LocalDateTime.now(ZoneOffset.UTC).withNano(0),
                Map.of("media_id", mediaId, "purpose", MediaPurpose.BRAND_LOGO.name()));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user(ADMIN).roles("ADMIN");
    }
}
