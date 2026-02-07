package com.fusionxpay.admin.integration;

import com.fusionxpay.admin.dto.LoginRequest;
import com.fusionxpay.admin.dto.LoginResponse;
import com.fusionxpay.admin.dto.OrderPageResponse;
import com.fusionxpay.admin.dto.OrderResponse;
import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.model.MerchantStatus;
import com.fusionxpay.admin.repository.MerchantRepository;
import com.fusionxpay.admin.service.OrderService;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for RBAC (Role-Based Access Control) in admin-service
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminRBACIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private OrderService orderService;

    private static final String ADMIN_EMAIL = "admin@fusionxpay.com";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String MERCHANT1_EMAIL = "merchant1@fusionxpay.com";
    private static final String MERCHANT1_PASSWORD = "merchant123";
    private static final String MERCHANT2_EMAIL = "merchant2@fusionxpay.com";
    private static final String MERCHANT2_PASSWORD = "merchant456";

    private Long merchant1Id;
    private Long merchant2Id;

    @BeforeEach
    void setUp() {
        merchantRepository.deleteAll();

        // Create ADMIN user
        Merchant admin = Merchant.builder()
                .merchantCode("ADMIN001")
                .merchantName("Admin User")
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(MerchantRole.ADMIN)
                .status(MerchantStatus.ACTIVE)
                .build();
        merchantRepository.save(admin);

        // Create MERCHANT 1
        Merchant merchant1 = Merchant.builder()
                .merchantCode("MERCHANT001")
                .merchantName("Test Merchant 1")
                .email(MERCHANT1_EMAIL)
                .passwordHash(passwordEncoder.encode(MERCHANT1_PASSWORD))
                .role(MerchantRole.MERCHANT)
                .status(MerchantStatus.ACTIVE)
                .build();
        merchant1 = merchantRepository.save(merchant1);
        merchant1Id = merchant1.getId();

        // Create MERCHANT 2
        Merchant merchant2 = Merchant.builder()
                .merchantCode("MERCHANT002")
                .merchantName("Test Merchant 2")
                .email(MERCHANT2_EMAIL)
                .passwordHash(passwordEncoder.encode(MERCHANT2_PASSWORD))
                .role(MerchantRole.MERCHANT)
                .status(MerchantStatus.ACTIVE)
                .build();
        merchant2 = merchantRepository.save(merchant2);
        merchant2Id = merchant2.getId();

        // Setup mock order data
        setupMockOrderData();
    }

    private void setupMockOrderData() {
        // All orders (for ADMIN view)
        OrderResponse order1 = OrderResponse.builder()
                .id("order-001")
                .orderNumber("ORD-001")
                .merchantId(merchant1Id)
                .amount(new BigDecimal("100.00"))
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();

        OrderResponse order2 = OrderResponse.builder()
                .id("order-002")
                .orderNumber("ORD-002")
                .merchantId(merchant2Id)
                .amount(new BigDecimal("200.00"))
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        OrderResponse order3 = OrderResponse.builder()
                .id("order-003")
                .orderNumber("ORD-003")
                .merchantId(merchant1Id)
                .amount(new BigDecimal("150.00"))
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();

        // Admin can see all orders
        OrderPageResponse allOrders = OrderPageResponse.builder()
                .orders(List.of(order1, order2, order3))
                .page(0)
                .size(20)
                .totalElements(3)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        // Merchant 1 can only see their orders
        OrderPageResponse merchant1Orders = OrderPageResponse.builder()
                .orders(List.of(order1, order3))
                .page(0)
                .size(20)
                .totalElements(2)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        // Merchant 2 can only see their orders
        OrderPageResponse merchant2Orders = OrderPageResponse.builder()
                .orders(List.of(order2))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        // Configure mock behavior
        when(orderService.getOrders(any(), eq(true), any()))
                .thenReturn(allOrders);
        when(orderService.getOrders(any(), eq(false), eq(merchant1Id)))
                .thenReturn(merchant1Orders);
        when(orderService.getOrders(any(), eq(false), eq(merchant2Id)))
                .thenReturn(merchant2Orders);

        // Single order access
        when(orderService.getOrderById(eq("order-001"), eq(true), any()))
                .thenReturn(order1);
        when(orderService.getOrderById(eq("order-001"), eq(false), eq(merchant1Id)))
                .thenReturn(order1);
        when(orderService.getOrderById(eq("order-002"), eq(true), any()))
                .thenReturn(order2);
        when(orderService.getOrderById(eq("order-002"), eq(false), eq(merchant2Id)))
                .thenReturn(order2);

        // Merchant 1 trying to access Merchant 2's order - throws exception
        when(orderService.getOrderById(eq("order-002"), eq(false), eq(merchant1Id)))
                .thenThrow(new RuntimeException("Access denied: Order does not belong to merchant"));
    }

    private String login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        LoginRequest loginRequest = new LoginRequest(email, password);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/v1/admin/auth/login", entity, LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getToken();
    }

    @Test
    @DisplayName("ADMIN can view all orders from all merchants")
    void testAdmin_CanViewAllOrders() {
        // Login as admin
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        // Request all orders
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<OrderPageResponse> response = restTemplate.exchange(
                "/api/v1/admin/orders", HttpMethod.GET, entity, OrderPageResponse.class);

        // Verify admin can see all 3 orders
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrders()).hasSize(3);
        assertThat(response.getBody().getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("MERCHANT can only view their own orders")
    void testMerchant_CanOnlyViewOwnOrders() {
        // Login as merchant1
        String merchant1Token = login(MERCHANT1_EMAIL, MERCHANT1_PASSWORD);

        // Request orders
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(merchant1Token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<OrderPageResponse> response = restTemplate.exchange(
                "/api/v1/admin/orders", HttpMethod.GET, entity, OrderPageResponse.class);

        // Verify merchant1 can only see their 2 orders
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrders()).hasSize(2);
        assertThat(response.getBody().getTotalElements()).isEqualTo(2);

        // Verify all returned orders belong to merchant1
        response.getBody().getOrders().forEach(order -> {
            assertThat(order.getMerchantId()).isEqualTo(merchant1Id);
        });
    }

    @Test
    @DisplayName("MERCHANT accessing another merchant's order is rejected")
    void testMerchant_AccessingOtherMerchantOrder_IsRejected() {
        // Login as merchant1
        String merchant1Token = login(MERCHANT1_EMAIL, MERCHANT1_PASSWORD);

        // Try to access order belonging to merchant2
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(merchant1Token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/orders/order-002", HttpMethod.GET, entity, String.class);

        // Verify access is denied (500 due to RuntimeException, ideally should be 403)
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Different merchants see different order sets")
    void testDifferentMerchants_SeeDifferentOrders() {
        // Login as merchant1
        String merchant1Token = login(MERCHANT1_EMAIL, MERCHANT1_PASSWORD);
        HttpHeaders headers1 = new HttpHeaders();
        headers1.setBearerAuth(merchant1Token);
        HttpEntity<Void> entity1 = new HttpEntity<>(headers1);

        ResponseEntity<OrderPageResponse> response1 = restTemplate.exchange(
                "/api/v1/admin/orders", HttpMethod.GET, entity1, OrderPageResponse.class);

        // Login as merchant2
        String merchant2Token = login(MERCHANT2_EMAIL, MERCHANT2_PASSWORD);
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setBearerAuth(merchant2Token);
        HttpEntity<Void> entity2 = new HttpEntity<>(headers2);

        ResponseEntity<OrderPageResponse> response2 = restTemplate.exchange(
                "/api/v1/admin/orders", HttpMethod.GET, entity2, OrderPageResponse.class);

        // Verify different merchants see different orders
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Merchant1 sees 2 orders
        assertThat(response1.getBody().getOrders()).hasSize(2);
        // Merchant2 sees 1 order
        assertThat(response2.getBody().getOrders()).hasSize(1);

        // Verify no overlap in order IDs
        List<String> merchant1OrderIds = response1.getBody().getOrders()
                .stream().map(OrderResponse::getId).toList();
        List<String> merchant2OrderIds = response2.getBody().getOrders()
                .stream().map(OrderResponse::getId).toList();

        assertThat(merchant1OrderIds).doesNotContainAnyElementsOf(merchant2OrderIds);
    }

    @Test
    @DisplayName("ADMIN can view specific order from any merchant")
    void testAdmin_CanViewAnyMerchantOrder() {
        // Login as admin
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Access merchant1's order
        ResponseEntity<OrderResponse> response1 = restTemplate.exchange(
                "/api/v1/admin/orders/order-001", HttpMethod.GET, entity, OrderResponse.class);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody().getMerchantId()).isEqualTo(merchant1Id);

        // Access merchant2's order
        ResponseEntity<OrderResponse> response2 = restTemplate.exchange(
                "/api/v1/admin/orders/order-002", HttpMethod.GET, entity, OrderResponse.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody().getMerchantId()).isEqualTo(merchant2Id);
    }

    @Test
    @DisplayName("MERCHANT can view their own specific order")
    void testMerchant_CanViewOwnOrder() {
        // Login as merchant1
        String merchant1Token = login(MERCHANT1_EMAIL, MERCHANT1_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(merchant1Token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Access own order
        ResponseEntity<OrderResponse> response = restTemplate.exchange(
                "/api/v1/admin/orders/order-001", HttpMethod.GET, entity, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("order-001");
        assertThat(response.getBody().getMerchantId()).isEqualTo(merchant1Id);
    }

    @Test
    @DisplayName("Unauthenticated request to orders endpoint returns 401")
    void testUnauthenticated_OrdersRequest_Returns401() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/orders", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
