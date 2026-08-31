package com.knowlink.api.bookings.controllers.interfaces;

import com.knowlink.api.bookings.controllers.responses.BookingCalendarResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/tutors/{tutorId}/booking-slots")
@Tag(name = "Reservas", description = "Calendario de horarios reservables de un tutor")
public interface IBookingCalendarController {

    @GetMapping
    @Operation(summary = "Obtener los horarios reservables de un tutor en un rango de fechas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Calendario obtenido"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "404", description = "Tutor no encontrado")
    })
    @ResponseStatus(OK)
    BookingCalendarResponse getCalendar(
            @PathVariable UUID tutorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
