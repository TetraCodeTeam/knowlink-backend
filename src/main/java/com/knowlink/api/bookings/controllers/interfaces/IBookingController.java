package com.knowlink.api.bookings.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.CREATED;

@RequestMapping("/api/v1/bookings")
@Tag(name = "Reservas", description = "Gestión de reservas de clases con tutores")
public interface IBookingController {

    @PostMapping
    @Operation(summary = "Reservar un turno con un tutor")
    @ApiResponse(responseCode = "201", description = "Reserva creada (pendiente de pago o confirmada si es gratuita)")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, antelación insuficiente, duración incorrecta, o límite diario/de holds superado")
    @ApiResponse(responseCode = "404", description = "Slot o materia no encontrados")
    @ApiResponse(responseCode = "409", description = "El horario ya fue reservado por otro alumno")
    @ResponseStatus(CREATED)
    BookingResponse createBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody @Valid CreateBookingRequest request
    );
}