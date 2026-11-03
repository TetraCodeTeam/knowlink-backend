package com.knowlink.api.users.validation;
import java.util.UUID;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.repositories.SessionClaimRepository;
import com.knowlink.api.exceptions.custom_exceptions.*;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.payments.repositories.IFundsTransferRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.Token;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.interfaces.ITokenService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_ACTIVE_BOOKINGS_CODE;
import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_ACTIVE_BOOKINGS_MESSAGE;
import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_ACTIVE_CLAIMS_CODE;
import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_ACTIVE_CLAIMS_MESSAGE;
import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_PENDING_OBLIGATIONS_CODE;
import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_PENDING_OBLIGATIONS_MESSAGE;
import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_RETAINED_FUNDS_CODE;
import static com.knowlink.api.users.utils.Constants.ACCOUNT_HAS_RETAINED_FUNDS_MESSAGE;

@Service
@RequiredArgsConstructor
public class UserValidationService implements IUserValidationService {

    private final IUserRepository userRepository;
    private final ITokenService tokenService;
    private final IBookingRepository bookingRepository;
    private final IHoldRepository holdRepository;
    private final SessionClaimRepository sessionClaimRepository;
    private final IFundsTransferRepository fundsTransferRepository;
    private final Clock clock;

    @Override
    public void ifEmailAlreadyExistsThrowException(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                    "DUPLICATE_EMAIL",
                    "Este correo ya está registrado.",
                    String.format("user con email '%s' ya existe", email));
        }
    }

    @Override
    public void ifDniAlreadyExistsThrowException(String dni) {
        if (userRepository.existsByDni(dni)) {
            throw new DuplicateResourceException(
                    "DUPLICATE_DNI",
                    "Este DNI ya está registrado.",
                    String.format("user con dni '%s' ya existe", dni));
        }
    }

    @Override
    public void ifDniBelongsToAnotherUserThrowException(String dni, UUID userId) {
        if (userRepository.existsByDniAndUserIdNot(dni, userId)) {
            throw new DuplicateResourceException(
                    "DUPLICATE_DNI",
                    "Este DNI ya está registrado.",
                    String.format("user con dni '%s' ya pertenece a otro usuario (editor userId '%s')", dni, userId));
        }
    }

    @Override
    public void verifyIfPasswordsMatch(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new PasswordsDoNotMatchException();
        }
    }

    @Override
    public void ifUserIsAlreadyActiveThrowException(User user) {
        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            throw new EmailAlreadyVerifiedException("El usuario ya ha verificado su cuenta.");
        }
    }

    @Override
    public void validateTokenNotExpired(Token token) {
        if (token.getTokenExpirationDate() == null ||
                token.getTokenExpirationDate().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("El enlace ha expirado. Por favor, solicita uno nuevo");
        }
    }

    @Override
    public void ifTokenDoesNotBelongToUserThrowException(Token token, UUID userId) {
        if (!token.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException(
                    "USER_NOT_FOUND",
                    "Usuario no encontrado.",
                    String.format("token pertenece a userId '%s', pero se recibió userId '%s'",
                            token.getUser().getUserId(), userId));
        }
    }

    @Override
    public void validateResendLimit(User user) {
        if (!tokenService.canResendToken(user)) {
            throw new TooManyRequestsException(
                    "Has excedido el límite de reenvíos. Por favor, intenta nuevamente más tarde.");
        }
    }

    @Override
    public void validateAtLeastOneAvailabilityParam(String email, String dni) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasDni = dni != null && !dni.isBlank();

        if (!hasEmail && !hasDni) {
            throw new ValidationException("Debes enviar al menos 'email' o 'dni' para verificar disponibilidad.");
        }
    }

    @Override
    public void ifUserHasActiveBookingsThrowException(UUID userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean blocked =
                bookingRepository.existsByStudent_UserIdAndBookingStatusIn(userId, BookingConstants.ACTIVE_STATUSES)
                        || bookingRepository.existsByTutor_UserIdAndBookingStatusIn(userId,
                                BookingConstants.ACTIVE_STATUSES)
                        || holdRepository.existsByStudent_UserIdAndExpiresAtAfter(userId, now)
                        || holdRepository.existsActiveHoldOnTutorSlots(userId, now);

        if (blocked) {
            throw new BusinessConflictException(
                    ACCOUNT_HAS_ACTIVE_BOOKINGS_CODE,
                    ACCOUNT_HAS_ACTIVE_BOOKINGS_MESSAGE,
                    String.format("user '%s' has active bookings or holds", userId));
        }
    }

    @Override
    public void ifUserHasActiveClaimsOrRetainedFundsThrowException(UUID userId) {
        if (sessionClaimRepository.existsActiveClaimForUser(userId, ClaimStatus.ACTIVE_STATUSES)) {
            throw new BusinessConflictException(
                    ACCOUNT_HAS_ACTIVE_CLAIMS_CODE,
                    ACCOUNT_HAS_ACTIVE_CLAIMS_MESSAGE,
                    String.format("user '%s' has active session claims", userId));
        }
        if (fundsTransferRepository.existsRetainedFundsForUser(userId, FundsStatus.RETAINED_STATUSES)) {
            throw new BusinessConflictException(
                    ACCOUNT_HAS_RETAINED_FUNDS_CODE,
                    ACCOUNT_HAS_RETAINED_FUNDS_MESSAGE,
                    String.format("user '%s' has retained funds", userId));
        }
    }

    @Override
    public void ifUserHasPendingObligationsThrowException(UUID userId) {
        boolean hasActiveClaim = sessionClaimRepository.existsActiveClaimForUser(
                userId, ClaimStatus.ACTIVE_STATUSES);
        boolean hasRetainedFunds = fundsTransferRepository.existsRetainedFundsForUser(
                userId, FundsStatus.RETAINED_STATUSES);

        if (hasActiveClaim || hasRetainedFunds) {
            throw new BusinessConflictException(
                    ACCOUNT_HAS_PENDING_OBLIGATIONS_CODE,
                    ACCOUNT_HAS_PENDING_OBLIGATIONS_MESSAGE,
                    String.format("user '%s' has pending obligations", userId));
        }
    }
}