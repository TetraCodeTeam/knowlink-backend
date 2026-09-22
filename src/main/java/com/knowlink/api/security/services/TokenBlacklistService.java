package com.knowlink.api.security.services;

import com.knowlink.api.security.data.models.TokenBlacklist;
import com.knowlink.api.security.repositories.ITokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final long CLEANUP_FIXED_DELAY_MS = 3_600_000;

    private final ITokenBlacklistRepository tokenBlacklistRepository;

    @Transactional
    public void blacklist(String jti, UUID userId, LocalDateTime expiresAt) {
        if (tokenBlacklistRepository.existsByJti(jti)) {
            return;
        }
        TokenBlacklist entry = TokenBlacklist.builder()
                .jti(jti)
                .userId(userId)
                .expiresAt(expiresAt)
                .build();
        tokenBlacklistRepository.save(entry);
        log.info("JWT invalidado: jti={}, userId={}, expiresAt={}", jti, userId, expiresAt);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        return tokenBlacklistRepository.existsByJti(jti);
    }

    @Scheduled(fixedDelay = CLEANUP_FIXED_DELAY_MS)
    @Transactional
    public void purgeExpiredTokens() {
        long deleted = tokenBlacklistRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Registros vencidos purgados de token_blacklist: {}", deleted);
        }
    }
}