package com.knowlink.api.bookings.controllers.interfaces;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.bookings.controllers.requests.ConfirmSessionTokenRequest;
import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingConfirmationResponse;
import com.knowlink.api.bookings.controllers.requests.VirtualSessionLinkRequest;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryDetailResponse;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryItemResponse;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

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
                        @RequestBody @Valid CreateBookingRequest request);

        @GetMapping("/mine")
        @Operation(summary = "Listar mis reservas por categoría (Reservadas/En Curso/Realizadas/Canceladas), paginado")
        @ApiResponse(responseCode = "200", description = "Listado paginado de reservas")
        @ApiResponse(responseCode = "403", description = "El rol solicitado no coincide con el de la cuenta autenticada")
        @ResponseStatus(OK)
        PagedResponse<BookingHistoryItemResponse> getHistory(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @RequestParam Role role,
                        @RequestParam BookingHistoryCategory category,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size);

        @GetMapping("/{bookingId}")
        @Operation(summary = "Ver el detalle completo de una reserva")
        @ApiResponse(responseCode = "200", description = "Detalle de la reserva")
        @ApiResponse(responseCode = "404", description = "La reserva no existe o no pertenece al usuario autenticado")
        @ResponseStatus(OK)
        BookingHistoryDetailResponse getDetail(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @PathVariable UUID bookingId);

        @PatchMapping("/{bookingId}/virtual-link")
        @Operation(summary = "Cargar o actualizar el link de la videollamada de una clase virtual")
        @ApiResponse(responseCode = "200", description = "Link actualizado")
        @ApiResponse(responseCode = "400", description = "La reserva no es virtual, o ya finalizó/fue cancelada")
        @ApiResponse(responseCode = "403", description = "El usuario autenticado no es el tutor de esta reserva")
        @ApiResponse(responseCode = "404", description = "La reserva no existe")
        @ResponseStatus(OK)
        BookingHistoryDetailResponse setVirtualLink(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @PathVariable UUID bookingId,
                        @Valid @RequestBody VirtualSessionLinkRequest request);

        @PatchMapping("/{bookingId}/confirmation")
        @Operation(summary = "Confirmar la sesión ingresando el código de 4 dígitos (US-41)")
        @ApiResponse(responseCode = "400", description = "Código incorrecto, expirado, o fuera de la ventana válida")
        @ResponseStatus(OK)
        BookingConfirmationResponse confirmSession(
                        @AuthenticationPrincipal UserPrincipal principal,
                        @PathVariable UUID bookingId,
                        @Valid @RequestBody ConfirmSessionTokenRequest request);
}