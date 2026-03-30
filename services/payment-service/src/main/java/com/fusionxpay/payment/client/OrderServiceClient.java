package com.fusionxpay.payment.client;

import com.fusionxpay.payment.dto.OrderResponse;
import com.fusionxpay.payment.dto.OrderStatusUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping("/api/v1/orders/id/{orderId}")
    ResponseEntity<OrderResponse> getOrderById(
            @RequestHeader("X-Merchant-Id") Long merchantId,
            @PathVariable UUID orderId
    );

    @PutMapping("/api/v1/orders/{orderId}/status")
    ResponseEntity<Void> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody OrderStatusUpdateRequest request
    );
}
