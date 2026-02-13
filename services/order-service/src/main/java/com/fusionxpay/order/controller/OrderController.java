package com.fusionxpay.order.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fusionxpay.order.constant.ApiResponseCodes;
import com.fusionxpay.order.dto.ApiErrorResponse;
import com.fusionxpay.order.dto.ApiValidationErrorResponse;
import com.fusionxpay.order.dto.OrderPageResponse;
import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.exception.ForbiddenException;
import com.fusionxpay.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order", description = "Order Service APIs")
public class OrderController {

    private final OrderService orderService;
    private final Validator validator;
    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";

    /**
     * Get paginated list of orders with optional filters
     */
    @GetMapping
    @Operation(summary = "Get orders with pagination", description = "Returns a paginated list of orders with optional filters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = ApiResponseCodes.OK, description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = ApiResponseCodes.FORBIDDEN, description = "Forbidden",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = ApiResponseCodes.INTERNAL_SERVER_ERROR, description = "Internal server error")
    })
    public ResponseEntity<OrderPageResponse> getOrders(
            @RequestHeader(value = HEADER_MERCHANT_ID, required = false) Long merchantIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String orderNumber) {
        log.info("Received get orders request - page: {}, size: {}, status: {}, merchantId: {}",
                page, size, status, merchantId);

        if (merchantIdHeader != null) {
            if (merchantId == null) {
                merchantId = merchantIdHeader;
            } else if (!merchantIdHeader.equals(merchantId)) {
                throw new ForbiddenException("Forbidden: merchantId mismatch");
            }
        }

        OrderPageResponse response = orderService.getOrders(page, size, status, merchantId, orderNumber);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order with the given order request details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = ApiResponseCodes.CREATED, description = "Order created successfully"),
        @ApiResponse(responseCode = ApiResponseCodes.BAD_REQUEST, description = "Invalid request parameters",
                content = @Content(schema = @Schema(implementation = ApiValidationErrorResponse.class))),
        @ApiResponse(responseCode = ApiResponseCodes.INTERNAL_SERVER_ERROR, description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = HEADER_MERCHANT_ID, required = false) Long merchantIdHeader,
            @RequestBody OrderRequest request
    ) {
        if (merchantIdHeader != null) {
            // Enforce that the order belongs to the authenticated merchant.
            request.setUserId(merchantIdHeader);
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        log.info("Received create order request for userId: {}", request.getUserId());
        
        OrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get order by order number", description = "Retrieves order details by its unique order number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = ApiResponseCodes.OK, description = "Order found and returned successfully"),
        @ApiResponse(responseCode = ApiResponseCodes.FORBIDDEN, description = "Forbidden",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = ApiResponseCodes.NOT_FOUND, description = "Order not found", 
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = ApiResponseCodes.INTERNAL_SERVER_ERROR, description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> getOrderByNumber(
            @RequestHeader(value = HEADER_MERCHANT_ID, required = false) Long merchantIdHeader,
            @PathVariable String orderNumber
    ) {
        log.info("Received get order request for number: {}", orderNumber);

        OrderResponse response = orderService.getOrderByNumber(orderNumber);
        enforceOwnership(response, merchantIdHeader);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/id/{orderId}")
    @Operation(summary = "Get order by ID", description = "Retrieves order details by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = ApiResponseCodes.OK, description = "Order found and returned successfully"),
        @ApiResponse(responseCode = ApiResponseCodes.FORBIDDEN, description = "Forbidden",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = ApiResponseCodes.NOT_FOUND, description = "Order not found", 
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = ApiResponseCodes.INTERNAL_SERVER_ERROR, description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> getOrderById(
            @RequestHeader(value = HEADER_MERCHANT_ID, required = false) Long merchantIdHeader,
            @PathVariable UUID orderId
    ) {
        log.info("Received get order request for ID: {}", orderId);

        OrderResponse response = orderService.getOrderById(orderId);
        enforceOwnership(response, merchantIdHeader);
        return ResponseEntity.ok(response);
    }

    private void enforceOwnership(OrderResponse response, Long merchantIdHeader) {
        if (merchantIdHeader == null) {
            // No merchant context means internal call: allow.
            return;
        }
        if (response == null || response.getUserId() == null || !merchantIdHeader.equals(response.getUserId())) {
            throw new ForbiddenException("Forbidden: order does not belong to merchant");
        }
    }
}
