package com.knowlink.api.tutors.support.controllers.interfaces;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import com.knowlink.api.tutors.support.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.tutors.support.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.tutors.support.controllers.responses.MaterialReportingPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.CREATED;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/tutors/support")
@Tag(name = "Soporte y solicitudes", description = "Seguimiento de reclamos y materiales denunciados por el tutor")
public interface ITutorSupportController {

    @GetMapping
    @Operation(summary = "Consultar reclamos de sesiones y materiales denunciados")
    @ApiResponse(responseCode = "200", description = "Solicitudes y reclamos del tutor")
    @ApiResponse(responseCode = "403", description = "La sección es exclusiva para tutores")
    @ResponseStatus(OK)
    TutorSupportResponse getSupport(@AuthenticationPrincipal UserPrincipal principal);

    @GetMapping("/material-policy")
    @Operation(summary = "Consultar la normativa de materiales y denuncias")
    @ApiResponse(responseCode = "200", description = "Formatos, motivos y proceso de revisión")
    @ResponseStatus(OK)
    MaterialReportingPolicyResponse getMaterialReportingPolicy(
            @AuthenticationPrincipal UserPrincipal principal);

    @GetMapping("/session-claims/{claimId}")
    @Operation(summary = "Consultar el detalle de un reclamo de sesión")
    @ApiResponse(responseCode = "200", description = "Detalle del reclamo, fondos, avance y evidencias")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para tutores")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe o no pertenece al tutor")
    @ResponseStatus(OK)
    TutorSupportClaimDetailResponse getClaimDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId);

    @PostMapping("/session-claims/{claimId}/comments")
    @Operation(summary = "Agregar un comentario al reclamo")
    @ApiResponse(responseCode = "201", description = "Comentario agregado al seguimiento del reclamo")
    @ApiResponse(responseCode = "400", description = "El comentario es obligatorio o demasiado largo")
    @ApiResponse(responseCode = "404", description = "El reclamo no existe")
    @ApiResponse(responseCode = "409", description = "El reclamo ya fue resuelto")
    @ResponseStatus(CREATED)
    ClaimCommentResponse addClaimComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID claimId,
            @RequestBody @Valid AddClaimCommentRequest request);

    @GetMapping("/materials/{materialId}")
    @Operation(summary = "Consultar el detalle de un material denunciado")
    @ApiResponse(responseCode = "200", description = "Datos del material, denuncias, auditor y descargo")
    @ApiResponse(responseCode = "404", description = "El material no existe, no tiene denuncias o no pertenece al tutor")
    @ResponseStatus(OK)
    TutorReportedMaterialDetailResponse getReportedMaterialDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID materialId);

    @PostMapping(value = "/materials/{materialId}/defense", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Presentar un descargo y pruebas de autoría para un material denunciado")
    @ApiResponse(responseCode = "201", description = "Descargo y evidencias registrados")
    @ApiResponse(responseCode = "400", description = "El descargo o los archivos no son válidos")
    @ApiResponse(responseCode = "403", description = "El material no pertenece al tutor autenticado")
    @ApiResponse(responseCode = "404", description = "El material denunciado no existe")
    @ApiResponse(responseCode = "409", description = "Ya se presentó un descargo o la revisión terminó")
    @ResponseStatus(CREATED)
    TutorReportedMaterialDetailResponse submitMaterialDefense(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID materialId,
            @RequestPart("request") @Valid SubmitMaterialDefenseRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files);
}
