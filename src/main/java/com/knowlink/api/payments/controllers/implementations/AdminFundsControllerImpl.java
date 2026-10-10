package com.knowlink.api.payments.controllers.implementations;

import com.knowlink.api.payments.services.FundsResolutionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "Admin Fondos", description = "Administracion de fondos y resolucion de disputas")
public class AdminFundsControllerImpl {

    private final FundsResolutionService fundsResolutionService;

    @PostMapping("/{bookingId}/funds/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reintentar resolucion de fondos tras resolver disputa")
    @ApiResponse(responseCode = "200", description = "Resolucion de fondos reintentada exitosamente")
    @ApiResponse(responseCode = "404", description = "No hay fondos suspendidos para esta reserva")
    public ResponseEntity<Map<String, String>> retryFundsResolution(
            @PathVariable UUID bookingId) {
        fundsResolutionService.retryAfterClaimResolution(bookingId);
        return ResponseEntity.ok(Map.of(
                "message", "Resolucion de fondos reintentada para la reserva " + bookingId));
    }
}