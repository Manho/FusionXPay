package com.fusionxpay.order.constant;

/**
 * HTTP API response codes as String constants for use in annotations
 */
public final class ApiResponseCodes {
    public static final String OK = "200";
    public static final String CREATED = "201";
    public static final String BAD_REQUEST = "400"; 
    public static final String FORBIDDEN = "403";
    public static final String NOT_FOUND = "404";
    public static final String INTERNAL_SERVER_ERROR = "500";
    
    private ApiResponseCodes() {
        // Private constructor to prevent instantiation
    }
}
