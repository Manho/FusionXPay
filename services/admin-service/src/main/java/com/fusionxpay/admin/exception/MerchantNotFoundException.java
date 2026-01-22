package com.fusionxpay.admin.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when merchant is not found
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(String message) {
        super(message);
    }

    public MerchantNotFoundException(Long id) {
        super("Merchant not found with id: " + id);
    }

    public MerchantNotFoundException(String field, String value) {
        super("Merchant not found with " + field + ": " + value);
    }
}
