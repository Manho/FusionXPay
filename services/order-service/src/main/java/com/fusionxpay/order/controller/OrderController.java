package com.fusionxpay.order.controller;


import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fusionxpay.order.constant.ApiResponseCodes;
import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.exception.OrderNotFoundException;
import com.fusionxpay.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order", description = "Order Service APIs")
public class OrderController {

    private final OrderService orderService;
    
    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order with the given order request details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = ApiResponseCodes.CREATED, description = "Order created successfully"),
        @ApiResponse(responseCode = ApiResponseCodes.BAD_REQUEST, description = "Invalid request parameters"),
        @ApiResponse(responseCode = ApiResponseCodes.INTERNAL_SERVER_ERROR, description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("Received create order request for userId: {}", request.getUserId());
        
        OrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get order by order number", description = "Retrieves order details by its unique order number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = ApiResponseCodes.OK, description = "Order found and returned successfully"),
        @ApiResponse(responseCode = ApiResponseCodes.NOT_FOUND, description = "Order not found", 
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
        @ApiResponse(responseCode = ApiResponseCodes.INTERNAL_SERVER_ERROR, description = "Internal server error")
    })
    public ResponseEntity<?> getOrderByNumber(@PathVariable String orderNumber) {
        log.info("Received get order request for number: {}", orderNumber);
        
        try {
            OrderResponse response = orderService.getOrderByNumber(orderNumber);
            return ResponseEntity.ok(response);
        } catch (OrderNotFoundException ex) {
            log.warn("Order not found with number: {}", orderNumber);
            ErrorResponseSchema errorResponse = new ErrorResponseSchema();
            
            errorResponse.setTimestamp(Instant.now().toString());
            errorResponse.setStatus(HttpStatus.NOT_FOUND.value());
            errorResponse.setError("Not Found");
            errorResponse.setMessage("Order not found with number: " + orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }
    
    @GetMapping("/id/{orderId}")
    @Operation(summary = "Get order by ID", description = "Retrieves order details by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = ApiResponseCodes.OK, description = "Order found and returned successfully"),
        @ApiResponse(responseCode = ApiResponseCodes.NOT_FOUND, description = "Order not found", 
                    content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))),
        @ApiResponse(responseCode = ApiResponseCodes.INTERNAL_SERVER_ERROR, description = "Internal server error")
    })
    public ResponseEntity<?> getOrderById(@PathVariable UUID orderId) {
        log.info("Received get order request for ID: {}", orderId);
        
        try {
            OrderResponse response = orderService.getOrderById(orderId);
            return ResponseEntity.ok(response);
        } catch (OrderNotFoundException ex) {
            log.warn("Order not found with ID: {}", orderId);
            ErrorResponseSchema errorResponse = new ErrorResponseSchema();
            
            errorResponse.setTimestamp(Instant.now().toString());
            errorResponse.setStatus(HttpStatus.NOT_FOUND.value());
            errorResponse.setError("Not Found");
            errorResponse.setMessage("Order not found with ID: " + orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    // Helper schema class for Swagger documentation
    private class ErrorResponseSchema {
        @Schema(description = "Error timestamp", example = "2025-03-05T12:34:56")
        private String timestamp;
        
        @Schema(description = "HTTP status code", example = "404")
        private int status;
        
        @Schema(description = "Error type", example = "Not Found")
        private String error;
        
        @Schema(description = "Error message", example = "Order not found with number: ORD-12345678")
        private String message;
        
        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
        
        public void setStatus(int status) {
            this.status = status;
        }
        
        public void setError(String error) {
            this.error = error;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}