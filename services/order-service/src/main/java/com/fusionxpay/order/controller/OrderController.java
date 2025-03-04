package com.fusionxpay.order.controller;

import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;
    
    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order with the given order request details")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("Received create order request");
        OrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get order by order number", description = "Retrieves order details by its unique order number")
    public ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable String orderNumber) {
        log.info("Received get order request for number: {}", orderNumber);
        OrderResponse response = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(response);
    }
    
}