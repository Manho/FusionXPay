package com.fusionxpay.admin.repository;

import com.fusionxpay.admin.model.AiAuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiAuthSessionRepository extends JpaRepository<AiAuthSession, Long> {

    Optional<AiAuthSession> findBySessionId(String sessionId);

    Optional<AiAuthSession> findByAuthorizationCode(String authorizationCode);

    Optional<AiAuthSession> findByDeviceCode(String deviceCode);

    Optional<AiAuthSession> findByUserCode(String userCode);

    Optional<AiAuthSession> findByRefreshToken(String refreshToken);

    @Query("""
            select session
            from AiAuthSession session
            where (session.refreshTokenExpiresAt is not null and session.refreshTokenExpiresAt < :now)
               or (session.refreshTokenExpiresAt is null and session.authorizationExpiresAt < :now)
            """)
    List<AiAuthSession> findExpiredSessions(Instant now);
}
