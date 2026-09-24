package com.xdpsx.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.catalog.category.api.CategoryController;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.media.api.MediaController;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards that documentation-only interfaces ({@code CategoryControllerApi}, {@code MediaControllerApi}) are still
 * discovered by Springdoc while Spring MVC behavior lives exclusively on the controllers.
 *
 * <p>Uses a focused MVC slice: only the two controllers, the Springdoc beans, and mocked services are
 * loaded. No database, Liquibase, or Cloudinary connection is started.
 */
@WebMvcTest(controllers = {CategoryController.class, MediaController.class})
@Import(SecurityConfigForControllerTests.class)
@ImportAutoConfiguration({
    SpringDocConfiguration.class,
    SpringDocConfigProperties.class,
    SpringDocWebMvcConfiguration.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private MediaService mediaService;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode openApi;

    @BeforeAll
    void fetchGeneratedOpenApi() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        openApi = objectMapper.readTree(json);
    }

    @Test
    void categoryById_shouldKeepOperationFromApiDocsInterface() {
        JsonNode operation = openApi.at("/paths/~1categories~1{category-id}/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("summary").asString()).isEqualTo("Get category by ID");
        assertThat(operation.path("tags").get(0).asString()).isEqualTo("Category API");

        JsonNode pathParameter = operation.path("parameters").get(0);
        assertThat(pathParameter.path("name").asString()).isEqualTo("category-id");
        assertThat(pathParameter.path("in").asString()).isEqualTo("path");
    }

    @Test
    void adminCategories_shouldInheritParameterObjectFromApiDocsInterface() {
        JsonNode parameters = openApi.at("/paths/~1admin~1categories/get/parameters");

        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters.findValuesAsString("name"))
                .contains("name", "publicFlg", "sort", "level", "pageNum", "pageSize");
        parameters.forEach(
                parameter -> assertThat(parameter.path("in").asString()).isEqualTo("query"));
    }

    @Test
    void mediaUpload_shouldKeepMultipartRequestBodyAndParameterDescription() {
        JsonNode operation = openApi.at("/paths/~1media~1image-upload/post");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("summary").asString()).isEqualTo("Upload image");
        assertThat(operation.path("requestBody").path("content").has("multipart/form-data"))
                .isTrue();
        assertThat(operation
                        .path("requestBody")
                        .path("content")
                        .path("multipart/form-data")
                        .path("schema")
                        .path("$ref")
                        .asString())
                .isEqualTo("#/components/schemas/CreateMediaDTO");

        JsonNode resourceParameter = operation.path("parameters").get(0);
        assertThat(resourceParameter.path("name").asString()).isEqualTo("resource");
        assertThat(resourceParameter.path("description").asString()).isEqualTo("category, brand,...");
    }

    @Test
    void categoryTree_shouldReturnArrayOfDtoSchemaWithoutWrapper() {
        JsonNode schema = openApi.at("/paths/~1categories~1tree/get/responses/200/content/*~1*/schema");

        assertThat(schema.path("type").asString()).isEqualTo("array");
        assertThat(schema.path("items").path("$ref").asString()).isEqualTo("#/components/schemas/CategoryTreeResponse");
        assertThat(openApi.at("/components/schemas/CategoryTreeResponse").isMissingNode())
                .isFalse();
    }

    @Test
    void paginatedList_shouldSeparateDataAndPaginationMetadata() {
        JsonNode properties = openApi.at("/components/schemas/PageResponseAdminCategoryResponse")
                .path("properties");

        assertThat(properties.has("data")).isTrue();
        assertThat(properties.path("data").path("type").asString()).isEqualTo("array");
        assertThat(properties.path("meta").path("$ref").asString()).isEqualTo("#/components/schemas/PageMetadata");

        JsonNode metadataProperties = openApi.at("/components/schemas/PageMetadata/properties");
        assertThat(metadataProperties.has("page")).isTrue();
        assertThat(metadataProperties.has("size")).isTrue();
        assertThat(metadataProperties.has("totalElements")).isTrue();
        assertThat(metadataProperties.has("totalPages")).isTrue();
    }

    @Test
    void mediaUpload_shouldReferenceDtoSchemaDirectlyForCreated() {
        JsonNode schema = openApi.at("/paths/~1media~1image-upload/post/responses/201/content/*~1*/schema");

        assertThat(schema.path("$ref").asString()).isEqualTo("#/components/schemas/ViewMediaDTO");
    }

    @Test
    void mediaDelete_shouldDocumentNoContent() {
        JsonNode response = openApi.at("/paths/~1media~1{id}/delete/responses/204");

        assertThat(response.isMissingNode()).isFalse();
        assertThat(response.path("description").asString()).isEqualTo("No Content");
        assertThat(response.path("content").isMissingNode()).isTrue();
    }

    @Test
    void mediaDelete_shouldKeepOperationFromApiDocsInterface() {
        JsonNode operation = openApi.at("/paths/~1media~1{id}/delete");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("summary").asString()).isEqualTo("Delete media");
        assertThat(operation.path("tags").get(0).asString()).isEqualTo("Media API");
    }
}
