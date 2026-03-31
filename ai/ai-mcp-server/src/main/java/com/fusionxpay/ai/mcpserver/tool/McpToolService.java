package com.fusionxpay.ai.mcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.dto.auth.MerchantSession;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import com.fusionxpay.ai.common.dto.order.OrderPageResult;
import com.fusionxpay.ai.common.dto.order.OrderRecord;
import com.fusionxpay.ai.common.dto.order.OrderSearchRequest;
import com.fusionxpay.ai.common.dto.order.OrderStatusResult;
import com.fusionxpay.ai.common.dto.payment.InitiatePaymentRequest;
import com.fusionxpay.ai.common.dto.payment.PaymentPageResult;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import com.fusionxpay.ai.common.dto.payment.PaymentSearchRequest;
import com.fusionxpay.ai.common.dto.payment.RefundPaymentRequest;
import com.fusionxpay.ai.common.dto.payment.RefundResult;
import com.fusionxpay.ai.common.exception.AiAuthenticationException;
import com.fusionxpay.ai.common.service.ConfirmationLookupResult;
import com.fusionxpay.ai.common.service.ConfirmationLookupStatus;
import com.fusionxpay.ai.common.service.ConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class McpToolService {

    private final GatewayClient gatewayClient;
    private final McpAuthenticationService authenticationService;
    private final ConfirmationService confirmationService;
    private final ObjectMapper objectMapper;

    @McpToolOperation("query_payment")
    public PaymentRecord queryPayment(String transactionId, String orderId) {
        return executeWithSession(session -> {
            if (hasText(transactionId)) {
                return gatewayClient.queryPayment(session.getToken(), session.getMerchant().getId(), parseUuid(transactionId, "transactionId"));
            }
            if (hasText(orderId)) {
                return gatewayClient.queryPaymentByOrder(session.getToken(), session.getMerchant().getId(), parseUuid(orderId, "orderId"));
            }
            throw new IllegalArgumentException("Provide either transactionId or orderId");
        });
    }

    @McpToolOperation("search_payments")
    public PaymentPageResult searchPayments(String status, String from, String to, int page, int size) {
        return executeWithSession(session -> gatewayClient.searchPayments(
                session.getToken(),
                session.getMerchant().getId(),
                PaymentSearchRequest.builder()
                        .status(status)
                        .from(from)
                        .to(to)
                        .page(page)
                        .size(size)
                        .build()
        ));
    }

    @McpToolOperation("get_order")
    public OrderRecord getOrder(String orderId, String orderNumber) {
        return executeWithSession(session -> {
            if (hasText(orderId)) {
                return gatewayClient.getOrderById(session.getToken(), session.getMerchant().getId(), parseUuid(orderId, "orderId"));
            }
            if (hasText(orderNumber)) {
                return gatewayClient.getOrderByNumber(session.getToken(), session.getMerchant().getId(), orderNumber);
            }
            throw new IllegalArgumentException("Provide either orderId or orderNumber");
        });
    }

    @McpToolOperation("search_orders")
    public OrderPageResult searchOrders(String status, String orderNumber, String from, String to, int page, int size) {
        return executeWithSession(session -> gatewayClient.searchOrders(
                session.getToken(),
                session.getMerchant().getId(),
                OrderSearchRequest.builder()
                        .status(status)
                        .orderNumber(orderNumber)
                        .from(from)
                        .to(to)
                        .page(page)
                        .size(size)
                        .build()
        ));
    }

    @McpToolOperation("get_order_status")
    public OrderStatusResult getOrderStatus(String orderId, String orderNumber) {
        OrderRecord order = getOrder(orderId, orderNumber);
        return OrderStatusResult.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    @McpToolOperation("initiate_payment")
    public ConfirmationResponse initiatePayment(InitiatePaymentRequest request) {
        return executeWithSession(session -> createPendingConfirmation(
                session,
                ConfirmationOperationType.INITIATE_PAYMENT,
                buildInitiatePaymentSummary(request),
                objectMapper.convertValue(request, Map.class)
        ));
    }

    @McpToolOperation("refund_payment")
    public ConfirmationResponse refundPayment(RefundPaymentRequest request) {
        return executeWithSession(session -> createPendingConfirmation(
                session,
                ConfirmationOperationType.REFUND_PAYMENT,
                buildRefundSummary(request),
                objectMapper.convertValue(request, Map.class)
        ));
    }

    @McpToolOperation("confirm_action")
    public ConfirmationResponse confirmAction(String token) {
        return executeWithSession(session -> {
            ConfirmationLookupResult lookupResult = confirmationService.consume(token, session.getMerchant().getId());
            if (lookupResult.getStatus() == ConfirmationLookupStatus.NOT_FOUND) {
                return rejection("Confirmation token not found");
            }
            if (lookupResult.getStatus() == ConfirmationLookupStatus.FORBIDDEN) {
                throw new AiAuthenticationException(403, "Confirmation token belongs to a different merchant", null, null);
            }
            PendingConfirmationAction action = lookupResult.getAction();
            if (lookupResult.getStatus() == ConfirmationLookupStatus.EXPIRED) {
                return ConfirmationResponse.builder()
                        .status(ConfirmationStatus.EXPIRED)
                        .token(token)
                        .operationType(action.getOperationType())
                        .summary(action.getSummary())
                        .expiresAt(action.getExpiresAt())
                        .build();
            }

            Object result = switch (action.getOperationType()) {
                case INITIATE_PAYMENT -> gatewayClient.initiatePayment(
                        session.getToken(),
                        session.getMerchant().getId(),
                        objectMapper.convertValue(action.getPayload(), InitiatePaymentRequest.class)
                );
                case REFUND_PAYMENT -> gatewayClient.refundPayment(
                        session.getToken(),
                        session.getMerchant().getId(),
                        objectMapper.convertValue(action.getPayload(), RefundPaymentRequest.class)
                );
            };

            return ConfirmationResponse.builder()
                    .status(ConfirmationStatus.CONFIRMED)
                    .token(token)
                    .operationType(action.getOperationType())
                    .summary(action.getSummary())
                    .result(result)
                    .build();
        });
    }

    private ConfirmationResponse createPendingConfirmation(MerchantSession session,
                                                           ConfirmationOperationType operationType,
                                                           String summary,
                                                           Map<String, Object> payload) {
        PendingConfirmationAction action = confirmationService.create(
                session.getMerchant().getId(),
                operationType,
                summary,
                payload
        );
        return ConfirmationResponse.builder()
                .status(action.getStatus())
                .token(action.getToken())
                .operationType(action.getOperationType())
                .summary(action.getSummary())
                .expiresAt(action.getExpiresAt())
                .build();
    }

    private <T> T executeWithSession(Function<MerchantSession, T> action) {
        MerchantSession session = authenticationService.getCurrentSession();
        try {
            return action.apply(session);
        } catch (AiAuthenticationException ex) {
            if (!authenticationService.supportsRefresh()) {
                throw ex;
            }
            return action.apply(authenticationService.refreshSession());
        }
    }

    private UUID parseUuid(String rawValue, String fieldName) {
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String buildInitiatePaymentSummary(InitiatePaymentRequest request) {
        return "Initiate " + safe(request.getPaymentChannel()) + " payment of "
                + safe(request.getCurrency()) + " " + safe(request.getAmount())
                + " for order " + safe(request.getOrderId());
    }

    private String buildRefundSummary(RefundPaymentRequest request) {
        return "Refund " + safe(request.getCurrency()) + " " + safe(request.getAmount())
                + " for transaction " + safe(request.getTransactionId());
    }

    private ConfirmationResponse rejection(String message) {
        return ConfirmationResponse.builder()
                .status(ConfirmationStatus.REJECTED)
                .summary(message)
                .build();
    }

    private String safe(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }
}
