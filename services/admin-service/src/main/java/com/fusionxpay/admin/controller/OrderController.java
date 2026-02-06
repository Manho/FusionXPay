package com.fusionxpay.admin.controller;

import com.fusionxpay.admin.dto.OrderPageResponse;
import com.fusionxpay.admin.dto.OrderQueryParams;
import com.fusionxpay.admin.dto.OrderResponse;
import com.fusionxpay.admin.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Order Controller - handles order queries with role-based access control
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    private final OrderService orderService;

    /**
     * Get paginated order list
     * ADMIN: can see all orders
     * MERCHANT: can only see their own orders
     */
    @GetMapping
    @Operation(summary = "Get orders", description = "Get paginated order list with optional filters")
    public ResponseEntity<OrderPageResponse> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {

        boolean isAdmin = isCurrentUserAdmin();
        Long merchantId = getMerchantIdFromRequest(request);

        OrderQueryParams params = OrderQueryParams.builder()
                .page(page)
                .size(size)
                .status(status)
                .orderNumber(orderNumber)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        OrderPageResponse response = orderService.getOrders(params, isAdmin, merchantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get order by ID
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "Get order detail", description = "Get single order by ID")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable String orderId,
            HttpServletRequest request) {

        boolean isAdmin = isCurrentUserAdmin();
        Long merchantId = getMerchantIdFromRequest(request);

        OrderResponse response = orderService.getOrderById(orderId, isAdmin, merchantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Check if current user has ADMIN role
     */
    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    /**
     * Get merchant ID from request (set by JWT filter)
     */
    private Long getMerchantIdFromRequest(HttpServletRequest request) {
        Object merchantId = request.getAttribute("merchantId");
        return merchantId != null ? (Long) merchantId : null;
    }
}
