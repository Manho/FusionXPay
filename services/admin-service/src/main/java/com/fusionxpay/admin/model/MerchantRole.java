package com.fusionxpay.admin.model;

/**
 * Merchant role enumeration
 */
public enum MerchantRole {
    ADMIN,      // System administrator - can view all orders
    MERCHANT    // Regular merchant - can only view own orders
}
