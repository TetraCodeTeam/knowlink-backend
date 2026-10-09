package com.knowlink.api.catalog.controllers.interfaces;

import com.knowlink.api.catalog.controllers.requests.CreateSubjectRequest;
import com.knowlink.api.catalog.controllers.requests.UpdateSubjectCareersRequest;
import com.knowlink.api.catalog.controllers.responses.CatalogSubjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog - Materias", description = "Materias del catálogo, scoped por institución")
public interface ICatalogSubjectController {

    @PostMapping("/institutions/{institutionId}/subjects")
    @Operation(summary = "Crear materia", description = "Crea una materia en la institución indicada y la asocia a las carreras indicadas. Si el nombre ya existe en la institución responde 409 con el id de la materia existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Materia creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o carreras de otra institución"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado (requiere rol ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Institución no encontrada"),
            @ApiResponse(responseCode = "409", description = "Ya existe una materia con ese nombre en la institución (MATERIA_DUPLICADA)")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CatalogSubjectResponse createSubject(
            @Parameter(description = "Identificador de la institución") @PathVariable UUID institutionId,
            @Valid @RequestBody CreateSubjectRequest request);

    @PatchMapping("/subjects/{subjectId}/careers")
    @Operation(summary = "Actualizar asociaciones de carrera de una materia", description = "Reemplaza (REPLACE, por defecto) o agrega (ADD) las carreras asociadas a la materia. No modifica el nombre de la materia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asociaciones actualizadas"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o carreras de otra institución"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado (requiere rol ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Materia no encontrada")
    })
    @PreAuthorize("hasRole('ADMIN')")
    CatalogSubjectResponse updateSubjectCareers(
            @Parameter(description = "Identificador de la materia") @PathVariable UUID subjectId,
            @Valid @RequestBody UpdateSubjectCareersRequest request);

    @GetMapping("/subjects")
    @Operation(summary = "Listar materias de una institución", description = "Con careerId devuelve las materias visibles para esa carrera (las de la carrera más las de la carrera COMPARTIDA de la institución). Sin careerId devuelve todas las materias de la institución.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de materias"),
            @ApiResponse(responseCode = "400", description = "Falta el parámetro institutionId"),
            @ApiResponse(responseCode = "404", description = "Institución o carrera no encontrada")
    })
    List<CatalogSubjectResponse> getSubjects(
            @Parameter(description = "Identificador de la institución (obligatorio)", required = true) @RequestParam UUID institutionId,
            @Parameter(description = "Identificador de la carrera (opcional)") @RequestParam(required = false) UUID careerId);
}