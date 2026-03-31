package com.fusionxpay.ai.mcpserver.tool;

import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import com.fusionxpay.ai.common.dto.order.OrderPageResult;
import com.fusionxpay.ai.common.dto.order.OrderRecord;
import com.fusionxpay.ai.common.dto.order.OrderStatusResult;
import com.fusionxpay.ai.common.dto.payment.InitiatePaymentRequest;
import com.fusionxpay.ai.common.dto.payment.PaymentPageResult;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import com.fusionxpay.ai.common.dto.payment.RefundPaymentRequest;
import com.fusionxpay.ai.common.dto.payment.RefundResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FusionXMcpTools {

    private final McpToolService toolService;

    @Tool(name = "query_payment", description = "Query a payment by transaction ID or order ID")
    public PaymentRecord queryPayment(
            @ToolParam(description = "Payment transaction UUID", required = false) String transactionId,
            @ToolParam(description = "Order UUID when transactionId is unknown", required = false) String orderId) {
        return toolService.queryPayment(transactionId, orderId);
    }

    @Tool(name = "search_payments", description = "Search merchant payments with optional status and date filters")
    public PaymentPageResult searchPayments(
            @ToolParam(description = "Optional payment status filter", required = false) String status,
            @ToolParam(description = "Optional start date in YYYY-MM-DD format", required = false) String from,
            @ToolParam(description = "Optional end date in YYYY-MM-DD format", required = false) String to,
            @ToolParam(description = "Zero-based page number", required = false) Integer page,
            @ToolParam(description = "Page size", required = false) Integer size) {
        return toolService.searchPayments(status, from, to, defaultNumber(page, 0), defaultNumber(size, 20));
    }

    @Tool(name = "get_order", description = "Get an order by order ID or order number")
    public OrderRecord getOrder(
            @ToolParam(description = "Order UUID", required = false) String orderId,
            @ToolParam(description = "Merchant order number", required = false) String orderNumber) {
        return toolService.getOrder(orderId, orderNumber);
    }

    @Tool(name = "search_orders", description = "Search merchant orders with optional status, order number, and date filters")
    public OrderPageResult searchOrders(
            @ToolParam(description = "Optional order status filter", required = false) String status,
            @ToolParam(description = "Optional order number filter", required = false) String orderNumber,
            @ToolParam(description = "Optional start date in YYYY-MM-DD format", required = false) String from,
            @ToolParam(description = "Optional end date in YYYY-MM-DD format", required = false) String to,
            @ToolParam(description = "Zero-based page number", required = false) Integer page,
            @ToolParam(description = "Page size", required = false) Integer size) {
        return toolService.searchOrders(status, orderNumber, from, to, defaultNumber(page, 0), defaultNumber(size, 20));
    }

    @Tool(name = "get_order_status", description = "Get only the status projection for an order")
    public OrderStatusResult getOrderStatus(
            @ToolParam(description = "Order UUID", required = false) String orderId,
            @ToolParam(description = "Merchant order number", required = false) String orderNumber) {
        return toolService.getOrderStatus(orderId, orderNumber);
    }

    @Tool(name = "initiate_payment", description = "Prepare a payment initiation request. This tool returns CONFIRMATION_REQUIRED and must be followed by confirm_action.")
    public ConfirmationResponse initiatePayment(
            @ToolParam(description = "Order UUID to pay", required = true) String orderId,
            @ToolParam(description = "Payment amount", required = true) String amount,
            @ToolParam(description = "Currency code, for example USD", required = true) String currency,
            @ToolParam(description = "Payment channel such as STRIPE or PAYPAL", required = true) String paymentChannel,
            @ToolParam(description = "Optional customer-facing description", required = false) String description,
            @ToolParam(description = "Optional provider return URL", required = false) String returnUrl,
            @ToolParam(description = "Optional provider cancel URL", required = false) String cancelUrl,
            @ToolParam(description = "Optional frontend success URL", required = false) String successUrl,
            @ToolParam(description = "Optional merchant reference", required = false) String merchantReference) {
        return toolService.initiatePayment(InitiatePaymentRequest.builder()
                .orderId(java.util.UUID.fromString(orderId))
                .amount(new java.math.BigDecimal(amount))
                .currency(currency)
                .paymentChannel(paymentChannel)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .successUrl(successUrl)
                .merchantReference(merchantReference)
                .build());
    }

    @Tool(name = "refund_payment", description = "Prepare a refund request for a merchant payment. This tool returns CONFIRMATION_REQUIRED and must be followed by confirm_action.")
    public ConfirmationResponse refundPayment(
            @ToolParam(description = "Payment transaction ID", required = true) String transactionId,
            @ToolParam(description = "Refund amount", required = false) String amount,
            @ToolParam(description = "Optional refund reason", required = false) String reason,
            @ToolParam(description = "Optional currency code", required = false) String currency,
            @ToolParam(description = "Optional PayPal capture ID", required = false) String captureId) {
        return toolService.refundPayment(RefundPaymentRequest.builder()
                .transactionId(transactionId)
                .amount(amount == null || amount.isBlank() ? null : new java.math.BigDecimal(amount))
                .reason(reason)
                .currency(currency)
                .captureId(captureId)
                .build());
    }

    @Tool(name = "confirm_action", description = "Execute a previously prepared write action using its confirmation token")
    public ConfirmationResponse confirmAction(
            @ToolParam(description = "Confirmation token returned by initiate_payment or refund_payment", required = true) String token) {
        return toolService.confirmAction(token);
    }

    private int defaultNumber(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
