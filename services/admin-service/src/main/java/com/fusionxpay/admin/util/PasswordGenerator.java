package com.fusionxpay.admin.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility to generate BCrypt password hashes
 * Run: mvn exec:java -Dexec.mainClass="com.fusionxpay.admin.util.PasswordGenerator"
 */
public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String admin123 = encoder.encode("admin123");
        String merchant123 = encoder.encode("merchant123");

        System.out.println("===========================================");
        System.out.println("BCrypt Password Hashes:");
        System.out.println("===========================================");
        System.out.println("admin123    -> " + admin123);
        System.out.println("merchant123 -> " + merchant123);
        System.out.println("===========================================");
    }
}
