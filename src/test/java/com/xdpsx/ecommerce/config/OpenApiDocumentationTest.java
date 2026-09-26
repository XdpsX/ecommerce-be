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

import com.xdpsx.ecommerce.catalog.category.api.AdminCategoryController;
import com.xdpsx.ecommerce.catalog.category.api.StorefrontCategoryController;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.media.api.MediaController;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards that documentation-only interfaces ({@code AdminCategoryApiDocs},
 * {@code StorefrontCategoryApiDocs},
 * {@code MediaControllerApi}) are still discovered by Springdoc while Spring
 * MVC behavior lives exclusively on the
 * controllers.
 *
 * <p>
 * Uses a focused MVC slice: only the controllers, the Springdoc beans, and
 * mocked services are loaded. No
 * database, Liquibase, or Cloudinary connection is started.
 */
@WebMvcTest(controllers = {AdminCategoryController.class, StorefrontCategoryController.class, MediaController.class})
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
    void adminCategoryById_shouldKeepOperationFromApiDocsInterface() {
        JsonNode operation = openApi.at("/paths/~1admin~1categories~1{id}/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("summary").asString()).isEqualTo("Get admin category by ID");
        assertThat(operation.path("tags").get(0).asString()).isEqualTo("Admin Category API");

        JsonNode pathParameter = operation.path("parameters").get(0);
        assertThat(pathParameter.path("name").asString()).isEqualTo("id");
        assertThat(pathParameter.path("in").asString()).isEqualTo("path");
    }

    @Test
    void adminCategories_shouldInheritParameterObjectFromApiDocsInterface() {
        JsonNode parameters = openApi.at("/paths/~1admin~1categories/get/parameters");

        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters.findValuesAsString("name"))
                .contains("name", "status", "parentId", "sort", "level", "pageNum", "pageSize");
        parameters.forEach(
                parameter -> assertThat(parameter.path("in").asString()).isEqualTo("query"));
    }

    @Test
    void adminCategoryRoutes_shouldBeResourceOrientedAndDropLegacyRpcPaths() {
        assertThat(openApi.at("/paths/~1admin~1categories").has("post")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1categories~1{id}").has("put")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1categories~1{id}").has("delete")).isTrue();

        // The legacy RPC paths must no longer be documented.
        assertThat(openApi.at("/paths/~1categories~1create").isMissingNode()).isTrue();
        assertThat(openApi.at("/paths/~1categories~1{id}~1update").isMissingNode())
                .isTrue();
        assertThat(openApi.at("/paths/~1categories~1{id}~1delete").isMissingNode())
                .isTrue();
        assertThat(openApi.at("/paths/~1categories~1exists").isMissingNode()).isTrue();
    }

    @Test
    void adminCategoryHierarchyRoutes_shouldBeDocumentedSeparatelyFromUpdate() {
        JsonNode move = openApi.at("/paths/~1admin~1categories~1{id}~1parent/put");
        assertThat(move.isMissingNode()).isFalse();
        assertThat(move.path("summary").asString()).isEqualTo("Move a category");
        assertThat(move.path("requestBody")
                        .path("content")
                        .path("application/json")
                        .path("schema")
                        .path("$ref")
                        .asString())
                .isEqualTo("#/components/schemas/MoveCategoryRequest");
        assertThat(move.path("responses").has("200")).isTrue();

        JsonNode reorder = openApi.at("/paths/~1admin~1categories~1order/put");
        assertThat(reorder.isMissingNode()).isFalse();
        assertThat(reorder.path("summary").asString()).isEqualTo("Reorder a sibling group");
        assertThat(reorder.path("responses").has("204")).isTrue();
    }

    @Test
    void updateCategorySchema_shouldNotExposeParentId() {
        JsonNode properties = openApi.at("/components/schemas/UpdateCategoryRequest/properties");

        assertThat(properties.has("parentId")).isFalse();
        assertThat(properties.has("name")).isTrue();

        JsonNode moveProperties = openApi.at("/components/schemas/MoveCategoryRequest/properties");
        assertThat(moveProperties.has("parentId")).isTrue();
        assertThat(moveProperties.has("position")).isTrue();

        JsonNode reorderProperties = openApi.at("/components/schemas/ReorderCategoriesRequest/properties");
        assertThat(reorderProperties.has("categoryIds")).isTrue();
    }

    @Test
    void storefrontTree_shouldStaySeparateFromAdminResponses() {
        JsonNode schema = openApi.at("/paths/~1categories~1tree/get/responses/200/content/*~1*/schema");

        assertThat(schema.path("type").asString()).isEqualTo("array");
        assertThat(schema.path("items").path("$ref").asString()).isEqualTo("#/components/schemas/CategoryTreeResponse");

        // The public tree node exposes the storefront fields and must not leak the
        // stored admin lifecycle.
        JsonNode treeProperties = openApi.at("/components/schemas/CategoryTreeResponse/properties");
        assertThat(treeProperties.has("slug")).isTrue();
        assertThat(treeProperties.has("status")).isFalse();
        assertThat(treeProperties.has("displayOrder")).isFalse();
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
    void storefrontCategoryReads_shouldDocumentRootListAndSlugDetail() {
        // Root list
        JsonNode rootSchema = openApi.at("/paths/~1categories/get/responses/200/content/*~1*/schema");
        assertThat(rootSchema.path("type").asString()).isEqualTo("array");
        assertThat(rootSchema.path("items").path("$ref").asString())
                .isEqualTo("#/components/schemas/StorefrontCategoryResponse");

        // Slug detail: path parameter and the shared 404 response for missing and
        // hidden categories.
        JsonNode detail = openApi.at("/paths/~1categories~1{slug}/get");
        assertThat(detail.isMissingNode()).isFalse();
        JsonNode slugParameter = detail.path("parameters").get(0);
        assertThat(slugParameter.path("name").asString()).isEqualTo("slug");
        assertThat(slugParameter.path("in").asString()).isEqualTo("path");
        assertThat(detail.path("responses").has("200")).isTrue();
        assertThat(detail.path("responses").has("404")).isTrue();
        assertThat(detail.at("/responses/200/content/*~1*/schema/$ref").asString())
                .isEqualTo("#/components/schemas/StorefrontCategoryResponse");

        // The public storefront schema must not leak admin lifecycle fields.
        JsonNode storefrontProperties = openApi.at("/components/schemas/StorefrontCategoryResponse/properties");
        assertThat(storefrontProperties.has("id")).isTrue();
        assertThat(storefrontProperties.has("name")).isTrue();
        assertThat(storefrontProperties.has("slug")).isTrue();
        assertThat(storefrontProperties.has("image")).isTrue();
        assertThat(storefrontProperties.has("status")).isFalse();
        assertThat(storefrontProperties.has("effectivelyActive")).isFalse();
        assertThat(storefrontProperties.has("displayOrder")).isFalse();
    }

    @Test
    void adminCategorySchema_shouldExposeStoredStatusAndEffectiveFlag() {
        JsonNode properties = openApi.at("/components/schemas/AdminCategoryResponse/properties");

        assertThat(properties.has("status")).isTrue();
        assertThat(properties.has("effectivelyActive")).isTrue();
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
    void problemSchemas_shouldDocumentCorrelationId() {
        JsonNode apiProblemProperties = openApi.at("/components/schemas/ApiProblem/properties");
        JsonNode validationProblemProperties = openApi.at("/components/schemas/ValidationProblem/properties");

        assertThat(apiProblemProperties.has("correlationId")).isTrue();
        assertThat(validationProblemProperties.has("correlationId")).isTrue();
        assertThat(apiProblemProperties.path("correlationId").path("example").asString())
                .isNotBlank();
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
