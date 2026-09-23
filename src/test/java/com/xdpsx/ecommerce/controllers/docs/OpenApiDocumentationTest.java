package com.xdpsx.ecommerce.controllers.docs;

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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xdpsx.ecommerce.SecurityConfigForControllerTests;
import com.xdpsx.ecommerce.controllers.CategoryController;
import com.xdpsx.ecommerce.controllers.MediaController;
import com.xdpsx.ecommerce.services.CategoryService;
import com.xdpsx.ecommerce.services.MediaService;

/**
 * Guards that documentation-only interfaces ({@code CategoryApiDocs}, {@code MediaApiDocs}) are still
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

    @MockBean
    private CategoryService categoryService;

    @MockBean
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
        assertThat(operation.path("summary").asText()).isEqualTo("Get category by ID");
        assertThat(operation.path("tags").get(0).asText()).isEqualTo("Category API");

        JsonNode pathParameter = operation.path("parameters").get(0);
        assertThat(pathParameter.path("name").asText()).isEqualTo("category-id");
        assertThat(pathParameter.path("in").asText()).isEqualTo("path");
    }

    @Test
    void adminCategories_shouldInheritParameterObjectFromApiDocsInterface() {
        JsonNode parameters = openApi.at("/paths/~1admin~1categories/get/parameters");

        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters.findValuesAsText("name"))
                .contains("name", "publicFlg", "sort", "level", "pageNum", "pageSize");
        parameters.forEach(
                parameter -> assertThat(parameter.path("in").asText()).isEqualTo("query"));
    }

    @Test
    void mediaUpload_shouldKeepMultipartRequestBodyAndParameterDescription() {
        JsonNode operation = openApi.at("/paths/~1media~1image-upload/post");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("summary").asText()).isEqualTo("Upload image");
        assertThat(operation.path("requestBody").path("content").has("multipart/form-data"))
                .isTrue();
        assertThat(operation
                        .path("requestBody")
                        .path("content")
                        .path("multipart/form-data")
                        .path("schema")
                        .path("$ref")
                        .asText())
                .isEqualTo("#/components/schemas/CreateMediaDTO");

        JsonNode resourceParameter = operation.path("parameters").get(0);
        assertThat(resourceParameter.path("name").asText()).isEqualTo("resource");
        assertThat(resourceParameter.path("description").asText()).isEqualTo("category, brand,...");
    }

    @Test
    void categoryTree_shouldReferenceConcreteWrapperSchema() {
        String schemaRef = openApi.at(
                        "/paths/~1categories~1tree/get/responses/200/content/application~1json/schema/$ref")
                .asText();

        assertThat(schemaRef).isEqualTo("#/components/schemas/GetCategoryTreeVM");
        assertThat(openApi.at("/components/schemas/GetCategoryTreeVM").isMissingNode())
                .isFalse();
    }

    @Test
    void mediaDelete_shouldKeepOperationFromApiDocsInterface() {
        JsonNode operation = openApi.at("/paths/~1media~1{id}/delete");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("summary").asText()).isEqualTo("Delete media");
        assertThat(operation.path("tags").get(0).asText()).isEqualTo("Media API");
    }
}
