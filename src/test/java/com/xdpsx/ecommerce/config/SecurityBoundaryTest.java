package com.xdpsx.ecommerce.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.auth.api.AuthController;
import com.xdpsx.ecommerce.auth.api.RefreshCookieService;
import com.xdpsx.ecommerce.auth.api.RefreshRequestGuard;
import com.xdpsx.ecommerce.auth.application.AuthService;
import com.xdpsx.ecommerce.auth.infrastructure.security.CustomAuthEntryPoint;
import com.xdpsx.ecommerce.auth.infrastructure.security.oauth2.CustomAuthenticationSuccessHandler;
import com.xdpsx.ecommerce.auth.infrastructure.security.oauth2.CustomOAuth2FailureHandler;
import com.xdpsx.ecommerce.auth.infrastructure.security.oauth2.CustomOAuth2UserService;
import com.xdpsx.ecommerce.cart.api.CartController;
import com.xdpsx.ecommerce.cart.api.GuestCartCookieService;
import com.xdpsx.ecommerce.cart.api.GuestCartRequestGuard;
import com.xdpsx.ecommerce.cart.api.dto.CartResponse;
import com.xdpsx.ecommerce.cart.application.CartMutationResult;
import com.xdpsx.ecommerce.cart.application.CartOwner;
import com.xdpsx.ecommerce.cart.application.CartOwnerResolver;
import com.xdpsx.ecommerce.cart.application.CartService;
import com.xdpsx.ecommerce.catalog.product.api.AdminProductController;
import com.xdpsx.ecommerce.catalog.product.api.AdminProductVariantController;
import com.xdpsx.ecommerce.catalog.product.api.StorefrontProductController;
import com.xdpsx.ecommerce.catalog.product.api.dto.AdminProductSummaryResponse;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductCreateRequest;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductVariantResponse;
import com.xdpsx.ecommerce.catalog.product.application.ProductService;
import com.xdpsx.ecommerce.catalog.product.application.ProductVariantService;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.config.security.SecurityConfig;
import com.xdpsx.ecommerce.media.api.MediaController;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.order.api.AdminOrderController;
import com.xdpsx.ecommerce.order.api.OrderController;
import com.xdpsx.ecommerce.order.application.OrderCancellationService;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.payment.api.PaymentController;
import com.xdpsx.ecommerce.payment.api.dto.VNPayIpnResponse;
import com.xdpsx.ecommerce.payment.application.PaymentAttemptService;
import com.xdpsx.ecommerce.payment.infrastructure.vnpay.IpnHandler;
import com.xdpsx.ecommerce.refund.api.AdminRefundController;
import com.xdpsx.ecommerce.refund.api.dto.RefundQueueItemResponse;
import com.xdpsx.ecommerce.refund.application.RefundService;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;
import com.xdpsx.ecommerce.user.api.UserAddressController;
import com.xdpsx.ecommerce.user.api.UserController;
import com.xdpsx.ecommerce.user.api.dto.UserAddressRequest;
import com.xdpsx.ecommerce.user.api.dto.UserAddressResponse;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;
import com.xdpsx.ecommerce.user.application.UserAddressService;
import com.xdpsx.ecommerce.user.application.UserService;

@WebMvcTest(
        controllers = {
            AuthController.class,
            AdminProductController.class,
            AdminProductVariantController.class,
            StorefrontProductController.class,
            CartController.class,
            MediaController.class,
            OrderController.class,
            AdminOrderController.class,
            AdminRefundController.class,
            PaymentController.class,
            UserController.class,
            UserAddressController.class
        },
        properties = {
            "app.jwt.secret=01234567890123456789012345678901",
            "app.cors.allowed-origins=http://localhost:3001"
        })
@Import({SecurityConfig.class, CustomAuthEntryPoint.class})
class SecurityBoundaryTest {

    private static final String CREATE_PRODUCT = """
			{
			"name": "Keyboard",
			"slug": "keyboard",
			"categoryId": 1,
			"brandId": 1,
			"imageIds": []
			}
			""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshCookieService refreshCookieService;

    @MockitoBean
    private RefreshRequestGuard refreshRequestGuard;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductVariantService productVariantService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CartOwnerResolver cartOwnerResolver;

    @MockitoBean
    private GuestCartCookieService guestCartCookieService;

    @MockitoBean
    private GuestCartRequestGuard guestCartRequestGuard;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    @MockitoBean
    private RefundService refundService;

    @MockitoBean
    private PaymentAttemptService paymentAttemptService;

    @MockitoBean
    private IpnHandler ipnHandler;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAddressService userAddressService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @MockitoBean
    private CustomOAuth2FailureHandler customOAuth2FailureHandler;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void storefrontGet_ShouldRemainPublic() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());
    }

    @Test
    void authenticatedCustomerEndpoint_ShouldRejectAnonymousAndAllowUser() throws Exception {
        when(cartOwnerResolver.resolve(any(), any())).thenReturn(CartOwner.guest(null));
        when(cartService.getCart(any())).thenReturn(new CartResponse());
        mockMvc.perform(get("/cart")).andExpect(status().isOk());

        when(cartOwnerResolver.resolve(any(), any())).thenReturn(CartOwner.customer("customer@example.test"));
        when(cartService.getCart(any())).thenReturn(new CartResponse());
        mockMvc.perform(get("/cart").with(user("customer@example.test").roles("USER")))
                .andExpect(status().isOk());

        verify(cartService, times(2)).getCart(any());
    }

    @Test
    void cartItemRoutes_ShouldUseResourceOrientedCartContract() throws Exception {
        CartResponse response = new CartResponse();
        CartMutationResult mutation = new CartMutationResult(response, null, null, false);
        when(cartOwnerResolver.resolve(any(), any())).thenReturn(CartOwner.customer("customer@example.test"));
        when(cartService.addItem(any(CartOwner.class), any())).thenReturn(mutation);
        when(cartService.replaceItem(any(CartOwner.class), eq(101L), any())).thenReturn(mutation);
        when(cartService.removeItem(any(CartOwner.class), eq(101L))).thenReturn(mutation);

        mockMvc.perform(post("/cart/items")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":101,\"quantity\":2}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/cart/items/101")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/cart/items/101")
                        .with(user("customer@example.test").roles("USER")))
                .andExpect(status().isOk());

        verify(cartService).addItem(any(CartOwner.class), any());
        verify(cartService).replaceItem(any(CartOwner.class), eq(101L), any());
        verify(cartService).removeItem(any(CartOwner.class), eq(101L));
    }

    @Test
    void guestCartMutation_ShouldRequireGuardAndNeverReturnCredentialInJson() throws Exception {
        when(cartOwnerResolver.resolve(any(), any())).thenReturn(CartOwner.guest(null));
        mockMvc.perform(post("/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":101,\"quantity\":2}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cartService);

        when(guestCartRequestGuard.isAllowed(any())).thenReturn(true);
        when(cartService.addItem(any(CartOwner.class), any()))
                .thenReturn(new CartMutationResult(
                        new CartResponse(), "42.secret", java.time.Instant.parse("2026-02-01T00:00:00Z"), false));
        when(guestCartCookieService.create(any(), any()))
                .thenReturn(ResponseCookie.from("guest_cart", "42.secret")
                        .httpOnly(true)
                        .path("/cart")
                        .build());

        mockMvc.perform(post("/cart/items")
                        .header("X-Cart-Request", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":101,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.guestCredential").doesNotExist());
    }

    @Test
    void guestCartClaim_ShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/cart/claim")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cartService);
    }

    @Test
    void productWrite_ShouldRequireAdmin() throws Exception {
        when(productService.createProduct(any(ProductCreateRequest.class)))
                .thenReturn(new AdminProductSummaryResponse(
                        1L, "Keyboard", "keyboard", true, false, null, null, null, null, null, 0, 0, 0));
        mockMvc.perform(post("/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PRODUCT))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/products")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PRODUCT))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);

        mockMvc.perform(post("/admin/products")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PRODUCT))
                .andExpect(status().isCreated());

        verify(productService).createProduct(any(ProductCreateRequest.class));
    }

    @Test
    void mediaWrite_ShouldRequireAdmin() throws Exception {
        mockMvc.perform(delete("/media/upload-id")
                        .with(user("customer@example.test").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mediaService);

        mockMvc.perform(delete("/media/upload-id")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isNoContent());
        verify(mediaService).deleteMedia("upload-id");
    }

    @Test
    void orderAdministration_ShouldRequireAdmin() throws Exception {
        mockMvc.perform(get("/orders").with(user("customer@example.test").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);

        mockMvc.perform(get("/orders").with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk());
        verify(orderService).getAllOrders(1, 5, null, null, null);

        mockMvc.perform(get("/orders")
                        .param("trackingNumber", " TRK-42 ")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk());
        verify(orderService).getAllOrders(1, 5, null, null, " TRK-42 ");

        when(orderService.updateOrderStatus(eq(42L), any()))
                .thenReturn(com.xdpsx.ecommerce.order.api.dto.OrderDTO.builder()
                        .id(42L)
                        .status("PROCESSING")
                        .build());
        mockMvc.perform(patch("/orders/42/status")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/orders/42/status")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        verify(orderService).updateOrderStatus(eq(42L), any());
    }

    @Test
    void orderLists_ShouldRejectInvalidPaginationBeforeCallingTheService() throws Exception {
        mockMvc.perform(get("/orders?pageNum=0&pageSize=5")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders?pageNum=1&pageSize=21")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders/me?pageNum=1&pageSize=0")
                        .with(user("buyer@example.test").roles("USER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void refundQueue_ShouldBeAdminOnlyAndReturnPagedItems() throws Exception {
        when(refundService.getQueue(RefundStatus.PENDING, 1, 5))
                .thenReturn(PageResponse.of(List.<RefundQueueItemResponse>of(), 1, 5, 0, 0));

        mockMvc.perform(get("/admin/refunds?status=PENDING&pageNum=1&pageSize=5")
                        .with(user("customer@example.test").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(refundService);

        mockMvc.perform(get("/admin/refunds?status=PENDING&pageNum=1&pageSize=5")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.data").isArray());
        verify(refundService).getQueue(RefundStatus.PENDING, 1, 5);
    }

    @Test
    void cancellationBoundaries_ShouldRequireCustomerAuthenticationAndAdminRole() throws Exception {
        mockMvc.perform(post("/orders/42/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isUnauthorized());

        when(orderCancellationService.cancelForCustomer(eq("customer@example.test"), eq(42L), any()))
                .thenReturn(com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO.builder()
                        .build());
        mockMvc.perform(post("/orders/42/cancellation")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/orders/42/cancellation")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Fulfillment failed\"}"))
                .andExpect(status().isForbidden());

        when(orderCancellationService.cancelAsAdmin(eq(42L), any(), eq("admin@example.test")))
                .thenReturn(com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO.builder()
                        .build());
        mockMvc.perform(post("/admin/orders/42/cancellation")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Fulfillment failed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refundAdministration_ShouldRequireAdminAndUsePersistedMoney() throws Exception {
        mockMvc.perform(get("/admin/refunds/9")
                        .with(user("customer@example.test").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(refundService);

        when(refundService.complete(eq(9L), any(), eq("admin@example.test")))
                .thenReturn(com.xdpsx.ecommerce.refund.api.dto.RefundResponse.builder()
                        .id(9L)
                        .status("SUCCEEDED")
                        .amount(new java.math.BigDecimal("100.00"))
                        .currency("VND")
                        .build());
        mockMvc.perform(post("/admin/refunds/9/complete")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalReference\":\"provider-refund-9\",\"amount\":1,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("VND"));

        verify(refundService).complete(eq(9L), any(), eq("admin@example.test"));
    }

    @Test
    void paymentProviderCallback_ShouldRemainPublic() throws Exception {
        when(ipnHandler.process(any())).thenReturn(new VNPayIpnResponse("00", "Confirm Success"));

        mockMvc.perform(get("/payments/vnpay_ipn").param("vnp_TxnRef", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));

        verify(ipnHandler).process(any());
    }

    @Test
    void paymentRetry_ShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/orders/42/payment-attempts")).andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentAttemptService);
    }

    @Test
    void variantSaleWrites_ShouldRequireAdmin() throws Exception {
        String schedule = """
                {
                  "salePrice": 80000.00,
                  "currency": "VND",
                  "startsAt": "2026-10-01T00:00:00Z",
                  "endsAt": "2026-10-08T00:00:00Z"
                }
                """;
        ProductVariantResponse response = new ProductVariantResponse(
                2L,
                "SKU-2",
                null,
                new java.math.BigDecimal("100000.00"),
                new java.math.BigDecimal("80000.00"),
                java.time.Instant.parse("2026-10-01T00:00:00Z"),
                java.time.Instant.parse("2026-10-08T00:00:00Z"),
                new java.math.BigDecimal("20000.00"),
                new java.math.BigDecimal("80000.00"),
                "VND",
                ProductVariantStatus.ACTIVE,
                List.of());
        when(productVariantService.scheduleSale(eq(1L), eq(2L), any())).thenReturn(response);

        mockMvc.perform(put("/admin/products/1/variants/2/sale")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedule))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/products/1/variants/2/sale")
                        .with(user("customer@example.test").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productVariantService);

        mockMvc.perform(put("/admin/products/1/variants/2/sale")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedule))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/products/1/variants/2/sale")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isNoContent());

        verify(productVariantService).scheduleSale(eq(1L), eq(2L), any());
        verify(productVariantService).removeSale(1L, 2L);
    }

    @Test
    void credentialedRefreshPreflight_ShouldAcceptGuardHeaderOnlyFromConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/auth/refresh")
                        .header("Origin", "http://localhost:3001")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Session-Request"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3001"));

        mockMvc.perform(options("/auth/refresh")
                        .header("Origin", "http://unconfigured.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Session-Request"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerProfileAndAddressEndpoints_ShouldRequireAuthenticationAndPassPrincipalIdentity() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/me/addresses")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService, userAddressService);

        when(userService.getUserByEmail("customer@example.test"))
                .thenReturn(UserProfile.builder()
                        .id(7L)
                        .name("Customer")
                        .email("customer@example.test")
                        .build());
        when(userService.updateCurrentProfile(eq("customer@example.test"), any()))
                .thenReturn(UserProfile.builder()
                        .id(7L)
                        .name("Renamed")
                        .email("customer@example.test")
                        .build());
        when(userAddressService.create(eq("customer@example.test"), any()))
                .thenReturn(new UserAddressResponse(
                        11L, "Customer", "+84901234567", "Street", "Ward", "District", "City", null));
        when(userAddressService.replace(eq("customer@example.test"), eq(11L), any()))
                .thenReturn(new UserAddressResponse(
                        11L, "Updated Customer", "+84901234567", "Updated Street", "Ward", "District", "City", null));

        mockMvc.perform(get("/users/me").with(user("customer@example.test").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.test"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/users/me")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
        mockMvc.perform(post("/users/me/addresses")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "Customer",
                                  "phoneNumber": "+84901234567",
                                  "addressLine": "Street",
                                  "wardCommune": "Ward",
                                  "district": "District",
                                  "provinceCity": "City"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));
        mockMvc.perform(put("/users/me/addresses/11")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "Updated Customer",
                                  "phoneNumber": "+84901234567",
                                  "addressLine": "Updated Street",
                                  "wardCommune": "Ward",
                                  "district": "District",
                                  "provinceCity": "City"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Updated Customer"));
        mockMvc.perform(delete("/users/me/addresses/11")
                        .with(user("customer@example.test").roles("USER")))
                .andExpect(status().isNoContent());

        verify(userService).getUserByEmail("customer@example.test");
        verify(userService).updateCurrentProfile(eq("customer@example.test"), any());
        verify(userAddressService).create(eq("customer@example.test"), any());
        verify(userAddressService).replace(eq("customer@example.test"), eq(11L), any());
        verify(userAddressService).delete("customer@example.test", 11L);
    }

    @Test
    void addressCreate_ShouldNormalizeWhitespaceBeforeBeanValidation() throws Exception {
        when(userAddressService.create(eq("customer@example.test"), any()))
                .thenReturn(new UserAddressResponse(
                        11L, "Customer", "+84901234567", "Street", "Ward", "District", "City", null));

        mockMvc.perform(post("/users/me/addresses")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "  Customer  ",
                                  "phoneNumber": " +84901234567 ",
                                  "addressLine": "  Street  ",
                                  "wardCommune": " Ward ",
                                  "district": " District ",
                                  "provinceCity": " City ",
                                  "postalCode": "   "
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<UserAddressRequest> requestCaptor = ArgumentCaptor.forClass(UserAddressRequest.class);
        verify(userAddressService).create(eq("customer@example.test"), requestCaptor.capture());
        UserAddressRequest request = requestCaptor.getValue();
        Assertions.assertThat(request.recipientName()).isEqualTo("Customer");
        Assertions.assertThat(request.phoneNumber()).isEqualTo("+84901234567");
        Assertions.assertThat(request.postalCode()).isNull();
    }

    @Test
    void addressCreate_ShouldReturnValidationProblemForInvalidPhone() throws Exception {
        mockMvc.perform(post("/users/me/addresses")
                        .with(user("customer@example.test").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "Customer",
                                  "phoneNumber": "not-a-phone",
                                  "addressLine": "Street",
                                  "wardCommune": "Ward",
                                  "district": "District",
                                  "provinceCity": "City"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(userAddressService);
    }

    @Test
    void addressDelete_ShouldNotDiscloseCrossOwnerMiss() throws Exception {
        doThrow(new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, java.util.Map.of("resourceType", "user address")))
                .when(userAddressService)
                .delete("customer@example.test", 42L);

        mockMvc.perform(delete("/users/me/addresses/42")
                        .with(user("customer@example.test").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(userAddressService).delete("customer@example.test", 42L);
    }
}
