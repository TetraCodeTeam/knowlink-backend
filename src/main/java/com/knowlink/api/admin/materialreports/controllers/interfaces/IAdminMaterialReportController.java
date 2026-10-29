package com.knowlink.api.admin.materialreports.controllers.interfaces;

import com.knowlink.api.admin.materialreports.controllers.requests.RemoveMaterialRequest;
import com.knowlink.api.admin.materialreports.controllers.responses.DownloadUrlResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseDetailResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListResponse;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.security.models.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RequestMapping("/api/v1/admin/material-reports")
@Tag(name = "Admin - Denuncias de material",
        description = "Gestión de denuncias de material inapropiado por parte de administradores")
public interface IAdminMaterialReportController {

    @Operation(summary = "Listar denuncias de material",
            description = "Devuelve los casos paginados, filtrables por estado y administrador asignado")
    @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    @ApiResponse(responseCode = "400", description = "Rango de fechas inválido")
    @GetMapping
    ResponseEntity<MaterialReportCaseListResponse> list(
            @Parameter(description = "Estado del caso (opcional)")
            @RequestParam(required = false) MaterialReportCaseStatus status,
            @Parameter(description = "Solo casos asignados al administrador autenticado")
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @Parameter(description = "Fecha desde (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha hasta (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Número de página (base 0)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamaño de página (máximo 50)")
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal UserPrincipal principal);

    @Operation(summary = "Obtener detalle de una denuncia",
            description = "Devuelve el resumen, el tutor, el material, las denuncias y los permisos del administrador")
    @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente")
    @ApiResponse(responseCode = "404", description = "La denuncia no existe")
    @GetMapping("/{caseId}")
    ResponseEntity<MaterialReportCaseDetailResponse> detail(
            @Parameter(description = "Identificador del caso") @PathVariable UUID caseId,
            @AuthenticationPrincipal UserPrincipal principal);

    @Operation(summary = "Tomar una denuncia",
            description = "Asigna el caso pendiente al administrador autenticado")
    @ApiResponse(responseCode = "200", description = "Caso tomado correctamente")
    @ApiResponse(responseCode = "404", description = "La denuncia no existe")
    @ApiResponse(responseCode = "409", description = "El caso ya fue tomado o no está pendiente")
    @PostMapping("/{caseId}/assign")
    ResponseEntity<MaterialReportCaseDetailResponse> assign(
            @Parameter(description = "Identificador del caso") @PathVariable UUID caseId,
            @AuthenticationPrincipal UserPrincipal principal);

    @Operation(summary = "Dar de baja el material denunciado",
            description = "Retira el material y resuelve todas las denuncias del caso")
    @ApiResponse(responseCode = "200", description = "Material dado de baja correctamente")
    @ApiResponse(responseCode = "403", description = "El administrador no es el asignado")
    @ApiResponse(responseCode = "404", description = "La denuncia no existe")
    @ApiResponse(responseCode = "409", description = "El caso ya fue resuelto o no está en revisión")
    @PostMapping("/{caseId}/remove")
    ResponseEntity<MaterialReportCaseDetailResponse> remove(
            @Parameter(description = "Identificador del caso") @PathVariable UUID caseId,
            @Valid @RequestBody(required = false) RemoveMaterialRequest request,
            @AuthenticationPrincipal UserPrincipal principal);

    @Operation(summary = "Desestimar una denuncia",
            description = "Mantiene el material disponible y resuelve todas las denuncias del caso como desestimadas")
    @ApiResponse(responseCode = "200", description = "Denuncia desestimada correctamente")
    @ApiResponse(responseCode = "403", description = "El administrador no es el asignado")
    @ApiResponse(responseCode = "404", description = "La denuncia no existe")
    @ApiResponse(responseCode = "409", description = "El caso ya fue resuelto o no está en revisión")
    @PostMapping("/{caseId}/dismiss")
    ResponseEntity<MaterialReportCaseDetailResponse> dismiss(
            @Parameter(description = "Identificador del caso") @PathVariable UUID caseId,
            @AuthenticationPrincipal UserPrincipal principal);

    @Operation(summary = "Obtener URL de descarga del material",
            description = "Genera una URL firmada temporal para auditar el archivo denunciado")
    @ApiResponse(responseCode = "200", description = "URL generada correctamente")
    @ApiResponse(responseCode = "404", description = "La denuncia o el archivo no existen")
    @GetMapping("/{caseId}/download-url")
    ResponseEntity<DownloadUrlResponse> downloadUrl(
            @Parameter(description = "Identificador del caso") @PathVariable UUID caseId,
            @AuthenticationPrincipal UserPrincipal principal);
}