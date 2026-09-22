package com.knowlink.api.security.services;

import com.knowlink.api.security.data.models.TokenBlacklist;
import com.knowlink.api.security.repositories.ITokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private ITokenBlacklistRepository tokenBlacklistRepository;

    private TokenBlacklistService service;

    private final UUID userId = UUID.randomUUID();
    private final LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

    @Test
    @DisplayName("Persiste el jti del token en logout")
    void blacklist_persistsEntry() {
        service = new TokenBlacklistService(tokenBlacklistRepository);
        when(tokenBlacklistRepository.existsByJti("jti-1")).thenReturn(false);

        service.blacklist("jti-1", userId, expiresAt);

        ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
        verify(tokenBlacklistRepository).save(captor.capture());
        TokenBlacklist saved = captor.getValue();
        assertThat(saved.getJti()).isEqualTo("jti-1");
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("No duplica el registro si el jti ya est\u00e1 blacklisteado")
    void blacklist_isIdempotent() {
        service = new TokenBlacklistService(tokenBlacklistRepository);
        when(tokenBlacklistRepository.existsByJti("jti-1")).thenReturn(true);

        service.blacklist("jti-1", userId, expiresAt);

        verify(tokenBlacklistRepository, never()).save(any());
    }

    @Test
    @DisplayName("isBlacklisted devuelve false para un jti nulo")
    void isBlacklisted_returnsFalseForNullJti() {
        service = new TokenBlacklistService(tokenBlacklistRepository);

        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted consulta el repositorio para un jti presente")
    void isBlacklisted_delegatesToRepository() {
        service = new TokenBlacklistService(tokenBlacklistRepository);
        when(tokenBlacklistRepository.existsByJti("jti-1")).thenReturn(true);

        assertThat(service.isBlacklisted("jti-1")).isTrue();
    }
}