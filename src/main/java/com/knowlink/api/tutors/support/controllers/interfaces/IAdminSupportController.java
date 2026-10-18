package com.knowlink.api.tutors.support.controllers.interfaces;

import com.knowlink.api.tutors.support.controllers.requests.UpdateClaimStatusRequest;
import com.knowlink.api.tutors.support.controllers.requests.UpdateMaterialReportStatusRequest;
import com.knowlink.api.tutors.support.controllers.responses.SupportStatusResponse;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RequestMapping("/api/v1/admin/support")
@Tag(name = "Administración de soporte", description = "Actualización del estado de reclamos y denuncias")
public interface IAdminSupportController {

    @GetMapping("/materials/{materialId}")
    @Operation(summary = "Consultar el detalle de un material denunciado")
    @ApiResponse(responseCode = "200", description = "Detalle de denuncias, archivo y descargo del tutor")
    @ApiResponse(responseCode = "404", description = "El material denunciado no existe")
    @ResponseStatus(HttpStatus.OK)
    TutorReportedMaterialDetailResponse getReportedMaterialDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID materialId);

    @PatchMapping("/session-claims/{claimId}")
    @Operation(summary = "Actualizar el estado de un reclamo")
    @ApiResponse(responseCode = "200", description = "Estado del reclamo actualizado")
    @ApiResponse(responseCode = "400", description = "La transición de estado no está permitida")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para administradores")
    @ResponseStatus(HttpStatus.OK)
    SupportStatusResponse updateClaimStatus(
            @PathVariable UUID claimId,
            @Valid @RequestBody UpdateClaimStatusRequest request);

    @PatchMapping("/material-reports/{reportId}")
    @Operation(summary = "Actualizar el estado de una denuncia de material")
    @ApiResponse(responseCode = "200", description = "Estado de la denuncia actualizado")
    @ApiResponse(responseCode = "400", description = "La transición de estado o su resultado no son válidos")
    @ApiResponse(responseCode = "404", description = "La denuncia no existe")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para administradores")
    @ResponseStatus(HttpStatus.OK)
    SupportStatusResponse updateMaterialReportStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody UpdateMaterialReportStatusRequest request);
}
