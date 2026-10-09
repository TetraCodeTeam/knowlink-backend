package com.knowlink.api.catalog.controllers.interfaces;

import com.knowlink.api.catalog.controllers.requests.CreateCareerRequest;
import com.knowlink.api.catalog.controllers.responses.CatalogCareerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/catalog/institutions/{institutionId}/careers")
@Tag(name = "Catalog - Carreras", description = "Carreras de cada institución, incluida la carrera reservada")
public interface ICatalogCareerController {

    @PostMapping
    @Operation(summary = "Crear carrera regular", description = "Crea una carrera de tipo REGULAR en la institución indicada. El tipo COMPARTIDA solo lo crea el sistema al dar de alta una institución.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Carrera creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o intento de crear una carrera COMPARTIDA manualmente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado (requiere rol ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Institución no encontrada"),
            @ApiResponse(responseCode = "409", description = "Ya existe una carrera con ese nombre en la institución")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CatalogCareerResponse createCareer(
            @Parameter(description = "Identificador de la institución") @PathVariable UUID institutionId,
            @Valid @RequestBody CreateCareerRequest request);

    @GetMapping
    @Operation(summary = "Listar carreras de una institución", description = "Incluye la carrera reservada, marcada con type COMPARTIDA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de carreras"),
            @ApiResponse(responseCode = "404", description = "Institución no encontrada")
    })
    List<CatalogCareerResponse> getCareers(
            @Parameter(description = "Identificador de la institución") @PathVariable UUID institutionId);
}