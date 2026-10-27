package com.knowlink.api.students.support.controllers.interfaces;

import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSupportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/students/support")
@Tag(name = "Reclamos del alumno", description = "Seguimiento de disputas de sesiones y denuncias de materiales")
public interface IStudentSupportController {

    @GetMapping
    @Operation(summary = "Consultar los reclamos y denuncias del alumno autenticado")
    @ApiResponse(responseCode = "200", description = "Historial y resumen de reclamos")
    @ApiResponse(responseCode = "403", description = "La sección es exclusiva para alumnos")
    @ResponseStatus(OK)
    StudentSupportResponse getSupport(@AuthenticationPrincipal UserPrincipal principal);

    @GetMapping("/material-policy")
    @Operation(summary = "Consultar la normativa de materiales y denuncias")
    @ApiResponse(responseCode = "200", description = "Formatos, motivos y proceso de revisión")
    @ApiResponse(responseCode = "403", description = "La sección es exclusiva para alumnos")
    @ResponseStatus(OK)
    MaterialReportingPolicyResponse getMaterialReportingPolicy(
            @AuthenticationPrincipal UserPrincipal principal);

    @GetMapping("/session-claims/{claimId}")
    @Operation(summary = "Consultar el detalle de una disputa de sesión propia")
    @ApiResponse(responseCode = "200", description = "Detalle de la disputa, fondos, seguimiento y evidencias")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe o no pertenece al alumno")
    @ResponseStatus(OK)
    StudentSessionClaimDetailResponse getSessionClaimDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId);

    @PostMapping("/session-claims/{claimId}/comments")
    @Operation(summary = "Aportar un comentario a un reclamo de sesión propio")
    @ApiResponse(responseCode = "201", description = "Comentario agregado al seguimiento del reclamo")
    @ApiResponse(responseCode = "400", description = "El comentario es obligatorio o demasiado largo")
    @ApiResponse(responseCode = "403", description = "La sección es exclusiva para alumnos")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe o no pertenece al alumno")
    @ApiResponse(responseCode = "409", description = "El reclamo ya fue resuelto")
    @ResponseStatus(CREATED)
    ClaimCommentResponse addClaimComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId,
            @RequestBody @Valid AddClaimCommentRequest request);

    @GetMapping("/material-reports/{reportId}")
    @Operation(summary = "Consultar el detalle de una denuncia de material propia")
    @ApiResponse(responseCode = "200", description = "Detalle de la denuncia y estado de revisión")
    @ApiResponse(responseCode = "404", description = "La denuncia no existe o no pertenece al alumno")
    @ResponseStatus(OK)
    StudentMaterialReportDetailResponse getMaterialReportDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reportId);
}
