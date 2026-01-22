package com.fusionxpay.order.service;

import com.fusionxpay.order.dto.OrderPageResponse;
import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.exception.OrderNotFoundException;
import com.fusionxpay.order.model.Order;
import com.fusionxpay.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public static final String NEW = "NEW";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Transactional
    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating new order for userId: {}", request.getUserId());
        
        // Generate a unique order number
        String orderNumber = generateOrderNumber();
        
        // Create and save the order
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(NEW)
                .build();
        
        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with orderId: {} and number: {}", savedOrder.getOrderId(), savedOrder.getOrderNumber());
        
        return mapToOrderResponse(savedOrder);
    }

    /**
     * Get paginated list of orders with optional filters
     */
    @Transactional(readOnly = true)
    public OrderPageResponse getOrders(int page, int size, String status, Long merchantId, String orderNumber) {
        log.info("Fetching orders - page: {}, size: {}, status: {}, merchantId: {}, orderNumber: {}",
                page, size, status, merchantId, orderNumber);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Use flexible filtering query
        String statusFilter = (status != null && !status.isEmpty()) ? status : null;
        String orderNumberFilter = (orderNumber != null && !orderNumber.isEmpty()) ? orderNumber : null;

        Page<Order> orderPage = orderRepository.findWithFilters(statusFilter, merchantId, orderNumberFilter, pageable);

        List<OrderResponse> orders = orderPage.getContent().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());

        return OrderPageResponse.builder()
                .orders(orders)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .first(orderPage.isFirst())
                .last(orderPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderByIdFallback")
    public OrderResponse getOrderById(UUID orderId) {
        log.info("Fetching order with orderId: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with orderId: " + orderId));
        
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        log.info("Fetching order with number: {}", orderNumber);
        
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with number: " + orderNumber));
        
        return mapToOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(String orderNumber, String newStatus) {
        log.info("Updating order status for order number: {} to: {}", orderNumber, newStatus);
        
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with number: " + orderNumber));
        
        // Validate the status transition
        validateStatusTransition(order.getStatus(), newStatus);
        
        // Update the status
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        
        return mapToOrderResponse(updatedOrder);
    }

    @Transactional
    public OrderResponse updateOrderStatusById(UUID orderId, String newStatus, String message) {
        log.info("Updating order status for order ID: {} to: {} with message: {}", orderId, newStatus, message);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with orderId: " + orderId));
        
        // Validate the status transition
        validateStatusTransition(order.getStatus(), newStatus);
        
        // Update the status
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        
        return mapToOrderResponse(updatedOrder);
    }

    // Circuit breaker fallback methods
    private OrderResponse createOrderFallback(OrderRequest request, Exception ex) {
        log.error("Fallback: Failed to create order due to: {}", ex.getMessage());
        // In a real system, consider implementing a retry queue or storing the request for later processing
        return OrderResponse.builder()
                .status("ERROR")
                .build();
    }
    
    private OrderResponse getOrderByIdFallback(UUID orderId, Exception ex) {
        if (ex instanceof OrderNotFoundException) {
            throw (OrderNotFoundException) ex;
        }
        log.error("Fallback: Failed to fetch order with orderId: {} due to: {}", orderId, ex.getMessage());
        return OrderResponse.builder()
                .status("ERROR")
                .build();
    }

    // Helper methods
    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private void validateStatusTransition(String currentStatus, String newStatus) {
        // Implement order status validation logic
        // Example: NEW can only transition to PROCESSING
        //          PROCESSING can transition to SUCCESS or FAILED
        //          SUCCESS and FAILED are terminal states
        
        boolean isValidTransition = switch (currentStatus) {
            case NEW -> newStatus.equals(PROCESSING);
            case PROCESSING -> newStatus.equals(SUCCESS) || newStatus.equals(FAILED);
            case SUCCESS, FAILED -> false; // Terminal states
            default -> false;
        };
        
        if (!isValidTransition) {
            throw new IllegalStateException(
                    "Invalid status transition from " + currentStatus + " to " + newStatus);
        }
    }
    
    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
