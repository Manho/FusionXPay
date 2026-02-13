package com.fusionxpay.admin.service;

import com.fusionxpay.admin.dto.OrderPageResponse;
import com.fusionxpay.admin.dto.OrderQueryParams;
import com.fusionxpay.admin.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Order Service - fetches orders from order-service via REST
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RestTemplate restTemplate;

    @Value("${services.order-service.url:http://localhost:8082}")
    private String orderServiceUrl;

    /**
     * Get paginated orders
     * For ADMIN: returns all orders
     * For MERCHANT: should filter by merchantId (to be implemented based on order-service)
     */
    public OrderPageResponse getOrders(OrderQueryParams params, boolean isAdmin, Long merchantId) {
        log.info("Fetching orders - isAdmin: {}, merchantId: {}, params: {}", isAdmin, merchantId, params);

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(orderServiceUrl + "/api/v1/orders")
                    .queryParam("page", params.getPage())
                    .queryParam("size", params.getSize());

            if (params.getStatus() != null) {
                builder.queryParam("status", params.getStatus());
            }
            if (params.getOrderNumber() != null) {
                builder.queryParam("orderNumber", params.getOrderNumber());
            }
            if (params.getStartDate() != null) {
                builder.queryParam("startDate", params.getStartDate());
            }
            if (params.getEndDate() != null) {
                builder.queryParam("endDate", params.getEndDate());
            }

            // For non-admin users, filter by merchant
            // Note: This requires order-service to support merchantId filtering
            if (!isAdmin && merchantId != null) {
                builder.queryParam("merchantId", merchantId);
            }

            String url = builder.toUriString();
            log.debug("Calling order-service: {}", url);

            ResponseEntity<OrderPageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    OrderPageResponse.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to fetch orders from order-service", e);
            // Return empty result on error
            return OrderPageResponse.builder()
                    .orders(List.of())
                    .page(params.getPage())
                    .size(params.getSize())
                    .totalElements(0)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .build();
        }
    }

    /**
     * Get order by ID
     */
    public OrderResponse getOrderById(String orderId, boolean isAdmin, Long merchantId) {
        log.info("Fetching order by ID: {} - isAdmin: {}, merchantId: {}", orderId, isAdmin, merchantId);

        try {
            // Align with order-service v1 routes.
            String url = orderServiceUrl + "/api/v1/orders/id/" + orderId;

            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    OrderResponse.class
            );

            OrderResponse order = response.getBody();

            // For non-admin users, verify order belongs to them
            // Note: This requires order-service to include merchantId in response
            // For now, we return the order (access control to be enhanced)
            if (order != null && !isAdmin) {
                // TODO: Add merchant ownership verification when order-service supports it
                log.debug("Order found for merchant verification");
            }

            return order;

        } catch (Exception e) {
            log.error("Failed to fetch order {} from order-service", orderId, e);
            throw new RuntimeException("Order not found: " + orderId);
        }
    }
}
