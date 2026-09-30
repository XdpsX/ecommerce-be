package com.xdpsx.ecommerce.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xdpsx.ecommerce.auth.infrastructure.security.CustomAuthEntryPoint;
import com.xdpsx.ecommerce.auth.infrastructure.security.oauth2.CustomAuthenticationSuccessHandler;
import com.xdpsx.ecommerce.auth.infrastructure.security.oauth2.CustomOAuth2FailureHandler;
import com.xdpsx.ecommerce.auth.infrastructure.security.oauth2.CustomOAuth2UserService;
import com.xdpsx.ecommerce.cart.api.CartController;
import com.xdpsx.ecommerce.cart.application.CartService;
import com.xdpsx.ecommerce.catalog.product.api.AdminProductController;
import com.xdpsx.ecommerce.catalog.product.api.StorefrontProductController;
import com.xdpsx.ecommerce.catalog.product.api.dto.AdminProductSummaryResponse;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductCreateRequest;
import com.xdpsx.ecommerce.catalog.product.application.ProductService;
import com.xdpsx.ecommerce.config.security.SecurityConfig;
import com.xdpsx.ecommerce.media.api.MediaController;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.order.api.OrderController;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.payment.api.PaymentController;
import com.xdpsx.ecommerce.payment.api.dto.VNPayIpnResponse;
import com.xdpsx.ecommerce.payment.infrastructure.vnpay.IpnHandler;

@WebMvcTest(
        controllers = {
            AdminProductController.class,
            StorefrontProductController.class,
            CartController.class,
            MediaController.class,
            OrderController.class,
            PaymentController.class
        },
        properties = {"app.jwt.secret=01234567890123456789012345678901", "app.cors.allowed-origins=http://localhost"})
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
    private ProductService productService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private IpnHandler ipnHandler;

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
        mockMvc.perform(get("/cart")).andExpect(status().isUnauthorized());

        when(cartService.getCart("customer@example.test")).thenReturn(List.of());
        mockMvc.perform(get("/cart").with(user("customer@example.test").roles("USER")))
                .andExpect(status().isOk());

        verify(cartService).getCart("customer@example.test");
    }

    @Test
    void productWrite_ShouldRequireAdmin() throws Exception {
        when(productService.createProduct(any(ProductCreateRequest.class)))
                .thenReturn(new AdminProductSummaryResponse(1L, "Keyboard", "keyboard", true, false, null, null, null));
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
        verify(orderService).getAllOrders(1, 5, null, null);
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
}
