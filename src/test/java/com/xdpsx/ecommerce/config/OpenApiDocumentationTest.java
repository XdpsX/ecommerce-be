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

import com.xdpsx.ecommerce.auth.api.AuthController;
import com.xdpsx.ecommerce.auth.api.RefreshCookieService;
import com.xdpsx.ecommerce.auth.api.RefreshRequestGuard;
import com.xdpsx.ecommerce.auth.application.AuthService;
import com.xdpsx.ecommerce.cart.api.CartController;
import com.xdpsx.ecommerce.cart.api.GuestCartCookieService;
import com.xdpsx.ecommerce.cart.api.GuestCartRequestGuard;
import com.xdpsx.ecommerce.cart.application.CartOwnerResolver;
import com.xdpsx.ecommerce.cart.application.CartService;
import com.xdpsx.ecommerce.catalog.brand.api.AdminBrandController;
import com.xdpsx.ecommerce.catalog.brand.api.StorefrontBrandController;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.catalog.category.api.AdminCategoryController;
import com.xdpsx.ecommerce.catalog.category.api.StorefrontCategoryController;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.catalog.product.api.AdminProductController;
import com.xdpsx.ecommerce.catalog.product.api.AdminProductVariantController;
import com.xdpsx.ecommerce.catalog.product.api.StorefrontProductController;
import com.xdpsx.ecommerce.catalog.product.application.ProductService;
import com.xdpsx.ecommerce.catalog.product.application.ProductVariantService;
import com.xdpsx.ecommerce.catalog.variantoption.api.AdminVariantOptionController;
import com.xdpsx.ecommerce.catalog.variantoption.application.VariantOptionService;
import com.xdpsx.ecommerce.media.api.MediaController;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.order.api.OrderController;
import com.xdpsx.ecommerce.order.application.OrderCancellationService;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.payment.application.PaymentAttemptService;
import com.xdpsx.ecommerce.refund.api.AdminRefundController;
import com.xdpsx.ecommerce.refund.application.RefundService;
import com.xdpsx.ecommerce.testsupport.SecurityConfigForControllerTests;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards that documentation-only interfaces ({@code AdminCategoryApiDocs},
 * {@code StorefrontCategoryApiDocs}, {@code AdminBrandApiDocs},
 * {@code AdminVariantOptionApiDocs}, {@code MediaControllerApi}) are still discovered by Springdoc while Spring
 * MVC behavior lives exclusively on the
 * controllers.
 *
 * <p>
 * Uses a focused MVC slice: only the controllers, the Springdoc beans, and
 * mocked services are loaded. No
 * database, Liquibase, or Cloudinary connection is started.
 */
@WebMvcTest(
        controllers = {
            AdminCategoryController.class,
            StorefrontCategoryController.class,
            AdminBrandController.class,
            StorefrontBrandController.class,
            AdminProductController.class,
            AdminProductVariantController.class,
            StorefrontProductController.class,
            AdminVariantOptionController.class,
            MediaController.class,
            CartController.class,
            OrderController.class,
            AuthController.class,
            AdminRefundController.class
        })
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
    private BrandService brandService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductVariantService productVariantService;

    @MockitoBean
    private VariantOptionService variantOptionService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CartOwnerResolver cartOwnerResolver;

    @MockitoBean
    private GuestCartCookieService guestCartCookieService;

    @MockitoBean
    private GuestCartRequestGuard guestCartRequestGuard;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    @MockitoBean
    private PaymentAttemptService paymentAttemptService;

    @MockitoBean
    private RefundService refundService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshCookieService refreshCookieService;

    @MockitoBean
    private RefreshRequestGuard refreshRequestGuard;

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
    void localAuthSessionRoutes_shouldDocumentAccessTokenOnlyAndRefreshLifecycle() {
        assertThat(openApi.at("/paths/~1auth~1register/post").isMissingNode()).isFalse();
        assertThat(openApi.at("/paths/~1auth~1login/post").isMissingNode()).isFalse();
        assertThat(openApi.at("/paths/~1auth~1refresh/post").isMissingNode()).isFalse();
        assertThat(openApi.at("/paths/~1auth~1logout/post").isMissingNode()).isFalse();
        assertThat(openApi.at("/paths/~1auth~1logout/post/responses/204").isMissingNode())
                .isFalse();

        JsonNode tokenProperties = openApi.at("/components/schemas/TokenResponse/properties");
        assertThat(tokenProperties.has("accessToken")).isTrue();
        assertThat(tokenProperties.has("refreshCredential")).isFalse();
    }

    @Test
    void adminBrandRoutes_shouldUseResourcePathsAndVersionedContracts() {
        assertThat(openApi.at("/paths/~1admin~1brands").has("get")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1brands").has("post")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1brands~1{id}").has("get")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1brands~1{id}").has("put")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1brands~1{id}").has("delete")).isTrue();

        assertThat(openApi.at("/paths/~1brands~1create").isMissingNode()).isTrue();
        assertThat(openApi.at("/paths/~1brands~1{id}~1update").isMissingNode()).isTrue();
        assertThat(openApi.at("/paths/~1brands~1{id}~1delete").isMissingNode()).isTrue();
        assertThat(openApi.at("/paths/~1brands~1exists").isMissingNode()).isTrue();

        JsonNode responseProperties = openApi.at("/components/schemas/AdminBrandResponse/properties");
        assertThat(responseProperties.has("status")).isTrue();
        assertThat(responseProperties.has("version")).isTrue();

        JsonNode updateProperties = openApi.at("/components/schemas/UpdateBrandRequest/properties");
        assertThat(updateProperties.has("version")).isTrue();
        assertThat(updateProperties.has("lastRetrievedAt")).isFalse();

        JsonNode deleteProperties = openApi.at("/components/schemas/DeleteBrandRequest/properties");
        assertThat(deleteProperties.has("version")).isTrue();
    }

    @Test
    void storefrontBrandRoute_shouldDocumentOptionalCategoryFilterAndPublicSchema() {
        JsonNode operation = openApi.at("/paths/~1brands/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("summary").asString()).isEqualTo("Get storefront brands");
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("404")).isTrue();
        assertThat(operation.at("/responses/200/content/*~1*/schema/items/$ref").asString())
                .isEqualTo("#/components/schemas/StorefrontBrandResponse");
        JsonNode categoryParameter = operation.path("parameters").get(0);
        assertThat(categoryParameter.path("name").asString()).isEqualTo("categoryId");
        assertThat(categoryParameter.path("in").asString()).isEqualTo("query");
        assertThat(categoryParameter.path("required").asBoolean()).isFalse();

        JsonNode properties = openApi.at("/components/schemas/StorefrontBrandResponse/properties");
        assertThat(properties.has("id")).isTrue();
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.has("image")).isTrue();
        assertThat(properties.has("status")).isFalse();
        assertThat(properties.has("version")).isFalse();
        assertThat(properties.has("categories")).isFalse();
    }

    @Test
    void adminProductList_shouldDocumentPriceAndInventoryTotals() {
        JsonNode properties = openApi.at("/components/schemas/AdminProductSummaryResponse/properties");

        assertThat(properties.has("minimumPrice")).isTrue();
        assertThat(properties.has("maximumPrice")).isTrue();
        assertThat(properties.has("onHand")).isTrue();
        assertThat(properties.has("reserved")).isTrue();
        assertThat(properties.has("available")).isTrue();
    }

    @Test
    void adminOrderAndRefundQueues_shouldDocumentTrackingAndPagedRefundFields() {
        JsonNode orderParameters = openApi.at("/paths/~1orders/get/parameters");
        assertThat(orderParameters.findValuesAsString("name")).contains("trackingNumber");

        JsonNode refundOperation = openApi.at("/paths/~1admin~1refunds/get");
        assertThat(refundOperation.isMissingNode()).isFalse();
        assertThat(refundOperation.path("parameters").findValuesAsString("name"))
                .contains("status", "pageNum", "pageSize");
        assertThat(refundOperation.at("/responses/200/content/*~1*/schema/$ref").asString())
                .isEqualTo("#/components/schemas/PageResponseRefundQueueItemResponse");

        JsonNode refundProperties = openApi.at("/components/schemas/RefundQueueItemResponse/properties");
        assertThat(refundProperties.has("trackingNumber")).isTrue();
        assertThat(refundProperties.has("status")).isTrue();
        assertThat(refundProperties.has("amount")).isTrue();
        assertThat(refundProperties.has("currency")).isTrue();
        assertThat(refundProperties.has("reason")).isTrue();
        assertThat(refundProperties.has("requestedAt")).isTrue();
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
        assertThat(resourceParameter.path("description").asString())
                .isEqualTo("category, brand, product, product-description");
    }

    @Test
    void productWriteRoutes_shouldUseJsonAndImageIds() {
        JsonNode create = openApi.at("/paths/~1admin~1products/post");
        JsonNode update = openApi.at("/paths/~1admin~1products~1{id}/put");
        assertThat(create.path("requestBody").path("content").has("application/json"))
                .isTrue();
        assertThat(create.path("requestBody").path("content").has("multipart/form-data"))
                .isFalse();
        assertThat(update.path("requestBody").path("content").has("application/json"))
                .isTrue();
        assertThat(openApi.at("/components/schemas/ProductCreateRequest/properties/imageIds")
                        .isMissingNode())
                .isFalse();
        assertThat(openApi.at("/components/schemas/ProductCreateRequest/properties/images")
                        .isMissingNode())
                .isTrue();
        assertThat(openApi.at("/components/schemas/ProductUpdateRequest/properties/removedImageIds")
                        .isMissingNode())
                .isTrue();
        assertThat(openApi.at("/components/schemas/ProductCreateRequest/properties/published")
                        .isMissingNode())
                .isTrue();
        assertThat(openApi.at("/components/schemas/ProductUpdateRequest/properties/published")
                        .isMissingNode())
                .isTrue();
        assertThat(openApi.at("/paths/~1products~1create").isMissingNode()).isTrue();
        assertThat(openApi.at("/paths/~1products~1{id}~1update").isMissingNode())
                .isTrue();
        assertThat(openApi.at("/paths/~1products~1{id}/get").isMissingNode()).isTrue();
        assertThat(openApi.at("/paths/~1products~1{id}~1delete/delete").isMissingNode())
                .isTrue();
        assertThat(openApi.at("/paths/~1products~1{id}~1public~1{status}/patch").isMissingNode())
                .isTrue();
        assertThat(openApi.at("/paths/~1products~1exists/get").isMissingNode()).isTrue();
        assertThat(openApi.at("/paths/~1admin~1products~1{id}~1publication/patch")
                        .isMissingNode())
                .isFalse();
        JsonNode adminSummaryProperties = openApi.at("/components/schemas/AdminProductSummaryResponse/properties");
        assertThat(adminSummaryProperties.has("brand")).isTrue();
        assertThat(adminSummaryProperties.has("category")).isTrue();
        JsonNode adminBrandProperties = openApi.at("/components/schemas/AdminProductBrandResponse/properties");
        assertThat(adminBrandProperties.has("id")).isTrue();
        assertThat(adminBrandProperties.has("name")).isTrue();
        assertThat(adminBrandProperties.has("status")).isTrue();
        JsonNode adminCategoryProperties = openApi.at("/components/schemas/AdminProductCategoryResponse/properties");
        assertThat(adminCategoryProperties.has("id")).isTrue();
        assertThat(adminCategoryProperties.has("name")).isTrue();
        assertThat(adminCategoryProperties.has("slug")).isTrue();
        assertThat(adminCategoryProperties.has("status")).isTrue();
        assertThat(adminCategoryProperties.has("effectivelyActive")).isTrue();
        JsonNode imageProperties = openApi.at("/components/schemas/ProductImageDTO/properties");
        assertThat(imageProperties.has("id")).isTrue();
        assertThat(imageProperties.has("url")).isTrue();
        assertThat(imageProperties.has("displayOrder")).isTrue();
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
        assertThat(properties.has("version")).isTrue();
        assertThat(properties.has("effectivelyActive")).isTrue();

        JsonNode updateProperties = openApi.at("/components/schemas/UpdateCategoryRequest/properties");
        assertThat(updateProperties.has("version")).isTrue();
        assertThat(updateProperties.has("lastRetrievedAt")).isFalse();

        JsonNode deleteProperties = openApi.at("/components/schemas/DeleteCategoryRequest/properties");
        assertThat(deleteProperties.has("version")).isTrue();
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

        assertThat(schema.path("$ref").asString()).isEqualTo("#/components/schemas/UploadedMediaDTO");
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

    @Test
    void adminVariantOptionRoutes_shouldExposeDictionaryResourcesAndLifecycleSchemas() {
        assertThat(openApi.at("/paths/~1admin~1variant-options").has("get")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1variant-options").has("post")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1variant-options~1{id}").has("get"))
                .isTrue();
        assertThat(openApi.at("/paths/~1admin~1variant-options~1{id}").has("put"))
                .isTrue();
        assertThat(openApi.at("/paths/~1admin~1variant-options~1{id}").has("delete"))
                .isFalse();
        assertThat(openApi.at("/paths/~1admin~1variant-options~1{optionId}~1values")
                        .has("post"))
                .isTrue();
        assertThat(openApi.at("/paths/~1admin~1variant-options~1{optionId}~1values~1{valueId}")
                        .has("put"))
                .isTrue();
        assertThat(openApi.at("/paths/~1admin~1variant-options~1{optionId}~1values~1{valueId}")
                        .has("delete"))
                .isFalse();
        assertThat(openApi.at("/paths/~1admin~1variant-options~1{optionId}~1values~1order")
                        .has("put"))
                .isTrue();

        JsonNode optionProperties = openApi.at("/components/schemas/VariantOptionResponse/properties");
        assertThat(optionProperties.has("code")).isTrue();
        assertThat(optionProperties.has("status")).isTrue();
        assertThat(optionProperties.has("values")).isTrue();

        JsonNode valueProperties = openApi.at("/components/schemas/VariantOptionValueResponse/properties");
        assertThat(valueProperties.has("code")).isTrue();
        assertThat(valueProperties.has("displayOrder")).isTrue();
        assertThat(valueProperties.has("status")).isTrue();
    }

    @Test
    void storefrontProductVariantReadModel_shouldExposeSelectionMatrixAndFacetRoute() {
        JsonNode filterOptions = openApi.at("/paths/~1products~1filter-options/get");
        assertThat(filterOptions.isMissingNode()).isFalse();
        assertThat(filterOptions.path("summary").asString()).isEqualTo("Get storefront product filter options");
        assertThat(filterOptions.at("/responses/200/content/*~1*/schema/type").asString())
                .isEqualTo("array");
        assertThat(filterOptions
                        .at("/responses/200/content/*~1*/schema/items/$ref")
                        .asString())
                .isEqualTo("#/components/schemas/ProductOptionResponse");

        assertThat(filterOptions.findValues("name").stream()
                        .map(JsonNode::asString)
                        .toList())
                .contains("categoryId", "brandId");
        JsonNode productList = openApi.at("/paths/~1products/get");
        assertThat(productList.path("parameters").findValues("name").stream()
                        .map(JsonNode::asString)
                        .toList())
                .contains("optionValueIds");

        JsonNode details = openApi.at("/components/schemas/StorefrontProductDetailResponse/properties");
        assertThat(details.has("options")).isTrue();
        assertThat(details.has("variants")).isTrue();
        assertThat(details.has("published")).isFalse();
        assertThat(openApi.at("/components/schemas/StorefrontProductFilter/properties/hasPublished")
                        .isMissingNode())
                .isTrue();

        JsonNode optionProperties = openApi.at("/components/schemas/ProductOptionResponse/properties");
        assertThat(optionProperties.has("status")).isFalse();
        assertThat(optionProperties.has("values")).isTrue();
        JsonNode variantProperties = openApi.at("/components/schemas/ProductVariantSelectionResponse/properties");
        assertThat(variantProperties.has("variantId")).isTrue();
        assertThat(variantProperties.has("sku")).isTrue();
        assertThat(variantProperties.has("optionValueIds")).isTrue();
        assertThat(variantProperties.has("available")).isTrue();

        JsonNode storefrontSummary = openApi.at("/components/schemas/StorefrontProductSummaryResponse/properties");
        assertThat(storefrontSummary.has("minimumPrice")).isTrue();
        assertThat(storefrontSummary.has("maximumPrice")).isTrue();
        assertThat(storefrontSummary.has("currency")).isTrue();
        assertThat(storefrontSummary.has("inStock")).isTrue();
        JsonNode storefrontDetail = openApi.at("/components/schemas/StorefrontProductDetailResponse/properties");
        assertThat(storefrontDetail.has("minimumPrice")).isTrue();
        assertThat(storefrontDetail.has("maximumPrice")).isTrue();
        assertThat(storefrontDetail.has("currency")).isTrue();
        assertThat(variantProperties.has("basePrice")).isTrue();
        assertThat(variantProperties.has("discountAmount")).isTrue();
        assertThat(variantProperties.has("finalUnitPrice")).isTrue();
        assertThat(variantProperties.has("currency")).isTrue();
        JsonNode cartItem = openApi.at("/components/schemas/CartItemResponse/properties");
        assertThat(cartItem.has("basePrice")).isTrue();
        assertThat(cartItem.has("discountAmount")).isTrue();
        assertThat(cartItem.has("finalUnitPrice")).isTrue();
        assertThat(cartItem.has("currency")).isTrue();
        assertThat(openApi.at("/paths/~1admin~1products~1{productId}~1variants~1{variantId}~1sale/put")
                        .isMissingNode())
                .isFalse();
        assertThat(openApi.at("/paths/~1admin~1products~1{productId}~1variants~1{variantId}~1sale/delete")
                        .isMissingNode())
                .isFalse();
        JsonNode orderItem = openApi.at("/components/schemas/OrderItemResponse/properties");
        assertThat(orderItem.has("unitBasePrice")).isTrue();
        assertThat(orderItem.has("discountAmount")).isTrue();
        assertThat(orderItem.has("finalUnitPrice")).isTrue();
        assertThat(orderItem.has("subtotal")).isTrue();
        assertThat(orderItem.has("currency")).isTrue();
        JsonNode paymentResponse = openApi.at("/components/schemas/InitPaymentResponse/properties");
        assertThat(paymentResponse.has("vnpUrl")).isTrue();
        assertThat(paymentResponse.has("attemptReference")).isTrue();
        assertThat(paymentResponse.has("expiresAt")).isTrue();
        JsonNode adminVariantProperties = openApi.at("/components/schemas/ProductVariantResponse/properties");
        assertThat(adminVariantProperties.has("salePrice")).isTrue();
        assertThat(adminVariantProperties.has("saleStartsAt")).isTrue();
        assertThat(adminVariantProperties.has("saleEndsAt")).isTrue();
        JsonNode productCreate = openApi.at("/components/schemas/ProductCreateRequest/properties");
        JsonNode productUpdate = openApi.at("/components/schemas/ProductUpdateRequest/properties");
        assertThat(productCreate.has("price")).isFalse();
        assertThat(productCreate.has("discountPercent")).isFalse();
        assertThat(productUpdate.has("price")).isFalse();
        assertThat(productUpdate.has("discountPercent")).isFalse();
        assertThat(productCreate.has("inStock")).isFalse();
        assertThat(productUpdate.has("inStock")).isFalse();
        assertThat(openApi.at("/components/schemas/ProductResponse/properties/inStock")
                        .isMissingNode())
                .isTrue();
    }
}
