package com.knowlink.api.users.services.interfaces;

import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface ITokenService {

    Token saveUserToken(User user);

    Token findByTokenOrThrowException(UUID tokenId);

    Token findLatestTokenByUser(User user);

    boolean canResendToken(User user);

    void deleteTokensByUser(User user);
}
