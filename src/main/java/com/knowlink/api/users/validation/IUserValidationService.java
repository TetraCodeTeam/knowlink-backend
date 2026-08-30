package com.knowlink.api.users.validation;
import java.util.UUID;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;

public interface IUserValidationService {

    void ifEmailAlreadyExistsThrowException(String email);

    void ifDniAlreadyExistsThrowException(String dni);

    void verifyIfPasswordsMatch(String password, String confirmPassword);

    void ifUserIsAlreadyActiveThrowException(User user);

    void validateTokenNotExpired(Token token);

    void ifTokenDoesNotBelongToUserThrowException(Token token, UUID userId);

    void validateResendLimit(User user);

    void validateAtLeastOneAvailabilityParam(String email, String dni);
}