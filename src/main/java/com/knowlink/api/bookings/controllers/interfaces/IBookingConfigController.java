package com.knowlink.api.bookings.controllers.interfaces;

import com.knowlink.api.bookings.controllers.responses.BookingConfigResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/bookings/config")
@Tag(name = "Reservas", description = "Configuración pública del módulo de reservas")
public interface IBookingConfigController {

    @GetMapping
    @Operation(summary = "Obtener parámetros públicos de configuración de reservas (ej. tarifa de servicio)")
    @ResponseStatus(OK)
    BookingConfigResponse getConfig();
}