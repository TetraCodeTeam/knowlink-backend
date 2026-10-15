package com.knowlink.api.claims.controllers.interfaces;

import com.knowlink.api.claims.controllers.requests.CreateClaimRequest;
import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimEligibilityResponse;
import com.knowlink.api.claims.controllers.responses.ClaimResponse;
import com.knowlink.api.security.models.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/bookings")
@Tag(name = "Reclamos", description = "Reclamos sobre sesiones finalizadas")
public interface IClaimController {

    @PostMapping(value = "/{bookingId}/claims", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Crear un reclamo sobre una sesión finalizada, con hasta 3 adjuntos (pdf, jpg o png)")
    @ApiResponse(responseCode = "201", description = "Reclamo creado y fondos de la sesión retenidos")
    @ApiResponse(responseCode = "400", description = "Motivo ausente o inválido, comentario demasiado largo, o adjuntos inválidos")
    @ApiResponse(responseCode = "401", description = "Sin autenticar")
    @ApiResponse(responseCode = "403", description = "El usuario no participa de la sesión")
    @ApiResponse(responseCode = "404", description = "La sesión no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe un reclamo activo del usuario para esta sesión")
    @ApiResponse(responseCode = "422", description = "La sesión no terminó, ya fue confirmada con token, no admite reclamos, venció el plazo, o el motivo no corresponde al rol del usuario")
    @ResponseStatus(CREATED)
    ClaimResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @RequestPart("request") @Valid CreateClaimRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files);

    @GetMapping("/{bookingId}/claims/eligibility")
    @Operation(summary = "Consultar si el usuario autenticado puede reclamar una sesión")
    @ApiResponse(responseCode = "200", description = "Elegibilidad de la sesión: puede reclamar, hasta cuándo, motivo de bloqueo si corresponde y motivos permitidos según el rol")
    @ApiResponse(responseCode = "404", description = "La sesión no existe")
    @ResponseStatus(OK)
    ClaimEligibilityResponse getEligibility(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId);

    @GetMapping("/{bookingId}/claims/{claimId}/attachments")
    @Operation(summary = "Listar los adjuntos de un reclamo con URL firmada")
    @ApiResponse(responseCode = "200", description = "Adjuntos del reclamo con URL firmada de lectura")
    @ApiResponse(responseCode = "403", description = "El usuario no participa de la sesión ni es administrador")
    @ApiResponse(responseCode = "404", description = "La sesión o el reclamo no existen")
    @ResponseStatus(OK)
    List<ClaimAttachmentUrlResponse> getAttachments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @PathVariable UUID claimId);
}