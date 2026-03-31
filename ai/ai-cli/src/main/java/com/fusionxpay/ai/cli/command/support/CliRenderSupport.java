package com.fusionxpay.ai.cli.command.support;

import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import com.fusionxpay.ai.common.dto.order.OrderPageResult;
import com.fusionxpay.ai.common.dto.order.OrderRecord;
import com.fusionxpay.ai.common.dto.order.OrderStatusResult;
import com.fusionxpay.ai.common.dto.payment.PaymentPageResult;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import com.fusionxpay.ai.common.dto.payment.RefundResult;

import java.io.PrintWriter;

public final class CliRenderSupport {

    private CliRenderSupport() {
    }

    public static String renderOrder(PrintWriter out, OrderRecord order) {
        out.printf("Order: %s%n", value(order.getOrderId()));
        out.printf("Number: %s%n", value(order.getOrderNumber()));
        out.printf("Status: %s%n", value(order.getStatus()));
        out.printf("Amount: %s %s%n", value(order.getCurrency()), value(order.getAmount()));
        out.printf("Merchant ID: %s%n", value(order.getUserId()));
        out.printf("Created At: %s%n", value(order.getCreatedAt()));
        return "order=" + value(order.getOrderId()) + ", status=" + value(order.getStatus());
    }

    public static String renderOrderStatus(PrintWriter out, OrderStatusResult statusResult) {
        out.printf("Order: %s%n", value(statusResult.getOrderId()));
        out.printf("Number: %s%n", value(statusResult.getOrderNumber()));
        out.printf("Status: %s%n", value(statusResult.getStatus()));
        out.printf("Updated At: %s%n", value(statusResult.getUpdatedAt()));
        return "order=" + value(statusResult.getOrderId()) + ", status=" + value(statusResult.getStatus());
    }

    public static String renderOrderPage(PrintWriter out, OrderPageResult pageResult) {
        out.printf("Orders: %d total (page %d/%d)%n",
                pageResult.getTotalElements(),
                pageResult.getPage(),
                Math.max(pageResult.getTotalPages() - 1, 0));
        for (OrderRecord order : pageResult.getOrders()) {
            out.printf("- %s | %s | %s %s | %s%n",
                    value(order.getOrderNumber()),
                    value(order.getOrderId()),
                    value(order.getCurrency()),
                    value(order.getAmount()),
                    value(order.getStatus()));
        }
        return "orders=" + pageResult.getTotalElements();
    }

    public static String renderPayment(PrintWriter out, PaymentRecord payment) {
        out.printf("Transaction: %s%n", value(payment.getTransactionId()));
        out.printf("Order: %s%n", value(payment.getOrderId()));
        out.printf("Status: %s%n", value(payment.getStatus()));
        out.printf("Amount: %s %s%n", value(payment.getCurrency()), value(payment.getAmount()));
        out.printf("Channel: %s%n", value(payment.getPaymentChannel()));
        if (payment.getRedirectUrl() != null && !payment.getRedirectUrl().isBlank()) {
            out.printf("Redirect URL: %s%n", payment.getRedirectUrl());
        }
        if (payment.getErrorMessage() != null && !payment.getErrorMessage().isBlank()) {
            out.printf("Error: %s%n", payment.getErrorMessage());
        }
        return "transaction=" + value(payment.getTransactionId()) + ", status=" + value(payment.getStatus());
    }

    public static String renderPaymentPage(PrintWriter out, PaymentPageResult pageResult) {
        out.printf("Payments: %d total (page %d/%d)%n",
                pageResult.getTotalElements(),
                pageResult.getPage(),
                Math.max(pageResult.getTotalPages() - 1, 0));
        for (PaymentRecord payment : pageResult.getPayments()) {
            out.printf("- %s | %s | %s %s | %s%n",
                    value(payment.getTransactionId()),
                    value(payment.getOrderId()),
                    value(payment.getCurrency()),
                    value(payment.getAmount()),
                    value(payment.getStatus()));
        }
        return "payments=" + pageResult.getTotalElements();
    }

    public static String renderConfirmation(PrintWriter out, ConfirmationResponse confirmationResponse) {
        out.printf("Status: %s%n", value(confirmationResponse.getStatus()));
        out.printf("Operation: %s%n", value(confirmationResponse.getOperationType()));
        out.printf("Summary: %s%n", value(confirmationResponse.getSummary()));
        if (confirmationResponse.getToken() != null && !confirmationResponse.getToken().isBlank()) {
            out.printf("Token: %s%n", confirmationResponse.getToken());
        }
        if (confirmationResponse.getExpiresAt() != null) {
            out.printf("Expires At: %s%n", confirmationResponse.getExpiresAt());
        }
        if (confirmationResponse.getResult() instanceof PaymentRecord paymentRecord) {
            out.println();
            return renderPayment(out, paymentRecord);
        }
        if (confirmationResponse.getResult() instanceof RefundResult refundResult) {
            out.println();
            return renderRefund(out, refundResult);
        }
        return "confirmation=" + value(confirmationResponse.getStatus());
    }

    public static String renderRefund(PrintWriter out, RefundResult refundResult) {
        out.printf("Refund Status: %s%n", value(refundResult.getStatus()));
        out.printf("Transaction: %s%n", value(refundResult.getTransactionId()));
        out.printf("Refund ID: %s%n", value(refundResult.getRefundId()));
        out.printf("Provider Refund ID: %s%n", value(refundResult.getProviderRefundId()));
        if (refundResult.getAmount() != null || refundResult.getCurrency() != null) {
            out.printf("Amount: %s %s%n", value(refundResult.getCurrency()), value(refundResult.getAmount()));
        }
        if (refundResult.getErrorMessage() != null && !refundResult.getErrorMessage().isBlank()) {
            out.printf("Error: %s%n", refundResult.getErrorMessage());
        }
        return "refundStatus=" + value(refundResult.getStatus()) + ", transaction=" + value(refundResult.getTransactionId());
    }

    private static String value(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }
}
