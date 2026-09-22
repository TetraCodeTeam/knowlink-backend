package com.knowlink.api.security.repositories;

import com.knowlink.api.security.data.models.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ITokenBlacklistRepository extends JpaRepository<TokenBlacklist, UUID> {

    boolean existsByJti(String jti);

    long deleteByExpiresAtBefore(LocalDateTime dateTime);
}