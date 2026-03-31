package com.fusionxpay.ai.cli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.fusionxpay.ai.common.service.ConfirmationLookupResult;
import com.fusionxpay.ai.common.service.ConfirmationLookupStatus;
import com.fusionxpay.ai.common.service.ConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CliCommandService {

    private final CliSessionService sessionService;
    private final ConfirmationService confirmationService;
    private final ObjectMapper objectMapper;

    public OrderRecord getOrder(String orderId, String orderNumber) {
        return withSession(session -> {
            if (hasText(orderId)) {
                return session.gatewayClient().getOrderById(session.token(), session.merchantId(), parseUuid(orderId, "orderId"));
            }
            if (hasText(orderNumber)) {
                return session.gatewayClient().getOrderByNumber(session.token(), session.merchantId(), orderNumber);
            }
            throw new IllegalArgumentException("Provide either orderId or orderNumber");
        });
    }

    public OrderPageResult searchOrders(String status, String orderNumber, String from, String to, int page, int size) {
        return withSession(session -> session.gatewayClient().searchOrders(
                session.token(),
                session.merchantId(),
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

    public OrderStatusResult getOrderStatus(String orderId, String orderNumber) {
        OrderRecord order = getOrder(orderId, orderNumber);
        return OrderStatusResult.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public PaymentRecord queryPayment(String transactionId, String orderId) {
        return withSession(session -> {
            if (hasText(transactionId)) {
                return session.gatewayClient().queryPayment(session.token(), session.merchantId(), parseUuid(transactionId, "transactionId"));
            }
            if (hasText(orderId)) {
                return session.gatewayClient().queryPaymentByOrder(session.token(), session.merchantId(), parseUuid(orderId, "orderId"));
            }
            throw new IllegalArgumentException("Provide either transactionId or orderId");
        });
    }

    public PaymentPageResult searchPayments(String status, String from, String to, int page, int size) {
        return withSession(session -> session.gatewayClient().searchPayments(
                session.token(),
                session.merchantId(),
                PaymentSearchRequest.builder()
                        .status(status)
                        .from(from)
                        .to(to)
                        .page(page)
                        .size(size)
                        .build()
        ));
    }

    public ConfirmationResponse initiatePayment(InitiatePaymentRequest request) {
        return withSession(session -> createPendingConfirmation(
                session,
                ConfirmationOperationType.INITIATE_PAYMENT,
                buildInitiatePaymentSummary(request),
                objectMapper.convertValue(request, Map.class)
        ));
    }

    public ConfirmationResponse refundPayment(RefundPaymentRequest request) {
        return withSession(session -> createPendingConfirmation(
                session,
                ConfirmationOperationType.REFUND_PAYMENT,
                buildRefundSummary(request),
                objectMapper.convertValue(request, Map.class)
        ));
    }

    public ConfirmationResponse confirmAction(String token) {
        return withSession(session -> {
            ConfirmationLookupResult lookupResult = confirmationService.consume(token, session.merchantId());
            if (lookupResult.getStatus() == ConfirmationLookupStatus.NOT_FOUND) {
                return ConfirmationResponse.builder()
                        .status(ConfirmationStatus.REJECTED)
                        .summary("Confirmation token not found")
                        .build();
            }
            if (lookupResult.getStatus() == ConfirmationLookupStatus.FORBIDDEN) {
                throw new IllegalStateException("Confirmation token belongs to a different merchant");
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
                case INITIATE_PAYMENT -> session.gatewayClient().initiatePayment(
                        session.token(),
                        session.merchantId(),
                        objectMapper.convertValue(action.getPayload(), InitiatePaymentRequest.class)
                );
                case REFUND_PAYMENT -> session.gatewayClient().refundPayment(
                        session.token(),
                        session.merchantId(),
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

    private ConfirmationResponse createPendingConfirmation(CliSessionService.ActiveSession session,
                                                           ConfirmationOperationType operationType,
                                                           String summary,
                                                           Map<String, Object> payload) {
        PendingConfirmationAction action = confirmationService.create(
                session.merchantId(),
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

    private <T> T withSession(SessionFunction<T> action) {
        return action.apply(sessionService.requireSession());
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

    private String safe(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }

    @FunctionalInterface
    private interface SessionFunction<T> {
        T apply(CliSessionService.ActiveSession session);
    }
}
