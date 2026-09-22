package com.knowlink.api.payments.controllers.interfaces;

import com.knowlink.api.payments.controllers.responses.FundsTransferResponseDTO;
import com.knowlink.api.security.models.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/v1/bookings")
@Tag(name = "Fondos", description = "Gestion de fondos y transferencias de reservas")
public interface IFundsController {

    @GetMapping("/{bookingId}/funds")
    @Operation(summary = "Consultar el estado de fondos y la transferencia de una reserva")
    @ApiResponse(responseCode = "200", description = "Estado de fondos de la reserva")
    @ApiResponse(responseCode = "404", description = "La reserva no tiene fondos asociados")
    ResponseEntity<FundsTransferResponseDTO> getFundsStatus(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId);
}