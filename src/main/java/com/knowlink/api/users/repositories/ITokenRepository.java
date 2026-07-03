package com.knowlink.api.users.repositories;

import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ITokenRepository extends JpaRepository<Token, UUID> {

    Optional<Token> findByTokenId(UUID tokenId);

    Optional<Token> findFirstByUserOrderByCreatedAtDesc(User user);

    long countByUserAndCreatedAtAfter(User user, LocalDateTime dateTime);

    void deleteAllByUser(User user);
}
