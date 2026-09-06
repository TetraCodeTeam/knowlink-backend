package com.knowlink.api.bookings.controllers.interfaces;

import com.knowlink.api.bookings.controllers.responses.BookingCancellationPreviewResponse;
import com.knowlink.api.bookings.controllers.responses.BookingCancellationResponse;
import com.knowlink.api.security.models.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/v1/bookings/{bookingId}/cancellations")
@Tag(name = "Cancelación de Reservas", description = "Cancelación de reservas confirmadas con política de reembolso")
public interface IBookingCancellationController {

    @PostMapping
    @Operation(summary = "Cancelar una reserva confirmada")
    @ApiResponse(responseCode = "200", description = "Reserva cancelada exitosamente")
    @ApiResponse(responseCode = "403", description = "El usuario no es alumno ni tutor de la reserva")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    @ApiResponse(responseCode = "409", description = "La reserva no está en estado confirmada")
    @ApiResponse(responseCode = "422", description = "La sesión ya ocurrió")
    BookingCancellationResponse cancelBooking(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal UserPrincipal principal);

    @GetMapping("/preview")
    @Operation(summary = "Previsualizar la política de cancelación aplicable")
    @ApiResponse(responseCode = "200", description = "Preview de la cancelación")
    @ApiResponse(responseCode = "403", description = "El usuario no es alumno ni tutor de la reserva")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    @ApiResponse(responseCode = "409", description = "La reserva no está en estado confirmada")
    @ApiResponse(responseCode = "422", description = "La sesión ya ocurrió")
    BookingCancellationPreviewResponse previewCancellation(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal UserPrincipal principal);
}
