package com.knowlink.api.users.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.ITokenRepository;
import com.knowlink.api.users.services.interfaces.ITokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.knowlink.api.users.utils.Constants.HOURS_LIMIT;
import static com.knowlink.api.users.utils.Constants.MAX_TOKENS_PER_HOUR;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements ITokenService {

    private final ITokenRepository tokenRepository;

    @Override
    public Token saveUserToken(User user) {
        Token token = Token.builder()
                .user(user)
                .build();
        return tokenRepository.save(token);
    }

    @Override
    public Token findByTokenOrThrowException(UUID tokenId) {
        return tokenRepository.findByTokenId(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TOKEN_NOT_FOUND",
                        "El enlace ya no es válido.",
                        String.format("token con id '%s' no existe", tokenId)));
    }

    @Override
    public Token findLatestTokenByUser(User user) {
        return tokenRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TOKEN_NOT_FOUND",
                        "El enlace ya no es válido.",
                        String.format("token para userId '%s' no existe", user.getUserId())));
    }

    @Override
    public boolean canResendToken(User user) {
        LocalDateTime hoursLimit = LocalDateTime.now().minusHours(HOURS_LIMIT);
        long tokensInLastHours = tokenRepository.countByUserAndCreatedAtAfter(user, hoursLimit);
        return tokensInLastHours < MAX_TOKENS_PER_HOUR;
    }

    @Override
    public void deleteTokensByUser(User user) {
        tokenRepository.deleteAllByUser(user);
    }
}