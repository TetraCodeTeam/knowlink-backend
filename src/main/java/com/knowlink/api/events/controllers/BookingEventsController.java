package com.knowlink.api.events.controllers;

import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tutors/{tutorId}/booking-slots")
@Tag(name = "Reservas", description = "Sincronización en tiempo real del calendario de reservas")
@RequiredArgsConstructor
public class BookingEventsController {

    private final BookingEventPublisher eventPublisher;
    private final ITutorProfileValidationService tutorProfileValidationService;

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Suscribirse a los cambios en tiempo real del calendario de un tutor")
    @ApiResponses({
             @ApiResponse(responseCode = "200", description = "Suscripción SSE iniciada"),
             @ApiResponse(responseCode = "403", description = "Acceso denegado"),
             @ApiResponse(responseCode = "404", description = "Tutor no encontrado")
     })
    public SseEmitter subscribe(@PathVariable UUID tutorId) {
        var tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorId);
        return eventPublisher.subscribe(tutorProfile.getTutorProfileId());
    }
}