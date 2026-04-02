package com.fusionxpay.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;

public class JwtUtils {

    private final SecretKey secretKey;

    public JwtUtils(String jwtSecret) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(JwtClaims claims, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(claims.email())
                .claim("merchantId", claims.merchantId())
                .claim("role", claims.role())
                .issuedAt(now)
                .expiration(expiryDate);

        if (claims.audience() != null && !claims.audience().isBlank()) {
            builder.claim("aud", claims.audience());
        }
        if (claims.tokenType() != null && !claims.tokenType().isBlank()) {
            builder.claim("tokenType", claims.tokenType());
        }

        return builder
                .signWith(secretKey)
                .compact();
    }

    public JwtClaims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new JwtClaims(
                claims.get("merchantId", Long.class),
                claims.getSubject(),
                claims.get("role", String.class),
                resolveAudience(claims),
                claims.get("tokenType", String.class)
        );
    }

    private String resolveAudience(Claims claims) {
        Collection<String> audience = claims.getAudience();
        if (audience != null && !audience.isEmpty()) {
            return audience.iterator().next();
        }
        return claims.get("aud", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            return false;
        } catch (ExpiredJwtException ex) {
            return false;
        } catch (UnsupportedJwtException ex) {
            return false;
        } catch (IllegalArgumentException ex) {
            return false;
        } catch (JwtException ex) {
            return false;
        }
    }
}
