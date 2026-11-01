package com.knowlink.api.users.repositories;

import com.knowlink.api.users.data.enums.TokenType;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ITokenRepository extends JpaRepository<Token, UUID> {

    Optional<Token> findByTokenId(UUID tokenId);

    Optional<Token> findByTokenIdAndTokenType(UUID tokenId, TokenType tokenType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Token t WHERE t.tokenId = :tokenId AND t.tokenType = :tokenType")
    Optional<Token> findByTokenIdAndTokenTypeForUpdate(@Param("tokenId") UUID tokenId,
                                                       @Param("tokenType") TokenType tokenType);

    Optional<Token> findFirstByUserOrderByCreatedAtDesc(User user);

    long countByUserAndCreatedAtAfter(User user, LocalDateTime dateTime);

    long countByUserAndTokenTypeAndCreatedAtAfter(User user, TokenType tokenType, LocalDateTime dateTime);

    void deleteAllByUser(User user);

    void deleteAllByUserAndTokenType(User user, TokenType tokenType);
}