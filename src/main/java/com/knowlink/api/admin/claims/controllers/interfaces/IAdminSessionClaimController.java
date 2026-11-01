package com.knowlink.api.admin.claims.controllers.interfaces;

import com.knowlink.api.admin.claims.controllers.requests.ResolveSessionClaimRequest;
import com.knowlink.api.admin.claims.controllers.responses.ClaimContactInfoResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimDetailResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.security.models.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

@RequestMapping("/api/v1/admin/session-claims")
@Tag(name = "Admin reclamos de sesión", description = "Resolución de disputas de sesión escaladas por el administrador")
public interface IAdminSessionClaimController {

    @GetMapping({"", "/"})
    @Operation(summary = "Listar reclamos de sesión escalados")
    @ApiResponse(responseCode = "200", description = "Reclamos paginados con contadores por estado")
    @ApiResponse(responseCode = "400", description = "Rango de fechas inválido")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para administradores")
    SessionClaimListResponse list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size);

    @GetMapping("/{claimId}")
    @Operation(summary = "Consultar el detalle de un reclamo de sesión")
    @ApiResponse(responseCode = "200", description = "Detalle del reclamo, declaraciones y permisos del administrador")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para administradores")
    SessionClaimDetailResponse getDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId);

    @PostMapping("/{claimId}/assign")
    @Operation(summary = "Tomar un reclamo de sesión para revisarlo")
    @ApiResponse(responseCode = "200", description = "Reclamo tomado y asignado al administrador actual")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe")
    @ApiResponse(responseCode = "409", description = "El reclamo ya fue tomado o resuelto")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para administradores")
    SessionClaimDetailResponse assign(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId);

    @GetMapping("/{claimId}/contact-info")
    @Operation(summary = "Consultar los datos de contacto de ambas partes")
    @ApiResponse(responseCode = "200", description = "Datos de contacto del tutor y del alumno")
    @ApiResponse(responseCode = "403", description = "El reclamo está asignado a otro administrador")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe")
    @ApiResponse(responseCode = "409", description = "El reclamo no está en revisión")
    ResponseEntity<ClaimContactInfoResponse> getContactInfo(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId);

    @PostMapping("/{claimId}/resolve")
    @Operation(summary = "Resolver un reclamo de sesión a favor de una de las partes")
    @ApiResponse(responseCode = "200", description = "Reclamo resuelto con la decisión y la justificación")
    @ApiResponse(responseCode = "400", description = "Falta la parte a favor o la justificación es inválida")
    @ApiResponse(responseCode = "403", description = "El reclamo está asignado a otro administrador")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe")
    @ApiResponse(responseCode = "409", description = "El reclamo ya fue resuelto, no está en revisión o los fondos no lo permiten")
    @ApiResponse(responseCode = "502", description = "No se pudo ejecutar el movimiento de fondos")
    SessionClaimDetailResponse resolve(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId,
            @Valid @RequestBody ResolveSessionClaimRequest request);
}