package com.knowlink.api.admin.exceptions;

import com.knowlink.api.exceptions.ApiError;
import com.knowlink.api.exceptions.GlobalExceptionHandler;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.FundsExecutionException;
import com.knowlink.api.exceptions.custom_exceptions.ImmutableBookingException;
import com.knowlink.api.exceptions.custom_exceptions.NotAssignedToAdminException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Conflicto de negocio: 409 con el código de error")
    void businessConflict_returns409() {
        ResponseEntity<ApiError> response = handler.handleBusinessConflict(
                new BusinessConflictException("CASE_ALREADY_TAKEN", "La denuncia ya fue tomada.", "technical"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("La denuncia ya fue tomada.");
        assertThat(response.getBody().detail()).isEqualTo("CASE_ALREADY_TAKEN");
    }

    @Test
    @DisplayName("Administrador no asignado: 403 NOT_ASSIGNED_TO_ADMIN")
    void notAssignedToAdmin_returns403() {
        ResponseEntity<ApiError> response = handler.handleNotAssignedToAdmin(
                new NotAssignedToAdminException("admin 1 is not assigned to case 2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().detail()).isEqualTo("NOT_ASSIGNED_TO_ADMIN");
    }

    @Test
    @DisplayName("Fallo de fondos: 502 FUNDS_EXECUTION_FAILED")
    void fundsExecution_returns502() {
        ResponseEntity<ApiError> response = handler.handleFundsExecution(
                new FundsExecutionException("ledger unavailable"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().detail()).isEqualTo("FUNDS_EXECUTION_FAILED");
    }

    @Test
    @DisplayName("Reserva inmutable: 409 BOOKING_IMMUTABLE")
    void immutableBooking_returns409() {
        ResponseEntity<ApiError> response = handler.handleImmutableBooking(
                new ImmutableBookingException(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().detail()).isEqualTo("BOOKING_IMMUTABLE");
    }

    @Test
    @DisplayName("Acceso denegado: 403 ACCESS_DENIED")
    void accessDenied_returns403WithCode() {
        ResponseEntity<ApiError> response = handler.handleAccessDenied(
                new AccessDeniedException("Access Denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().detail()).isEqualTo("ACCESS_DENIED");
    }
}
