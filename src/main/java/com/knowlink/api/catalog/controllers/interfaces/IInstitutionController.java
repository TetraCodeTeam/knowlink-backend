package com.knowlink.api.catalog.controllers.interfaces;

import com.knowlink.api.catalog.controllers.requests.CreateInstitutionRequest;
import com.knowlink.api.catalog.controllers.responses.InstitutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@RequestMapping("/api/v1/catalog/institutions")
@Tag(name = "Catalog - Instituciones", description = "Instituciones raíz del catálogo multi-institución")
public interface IInstitutionController {

    @PostMapping
    @Operation(summary = "Crear institución", description = "Crea la institución y, en la misma transacción, su carrera reservada de tipo COMPARTIDA.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Institución creada junto a su carrera reservada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado (requiere rol ADMIN)"),
            @ApiResponse(responseCode = "409", description = "Ya existe una institución con ese nombre")
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    InstitutionResponse createInstitution(@Valid @RequestBody CreateInstitutionRequest request);

    @GetMapping
    @Operation(summary = "Listar instituciones", description = "Uso público: alimenta los selectores de registro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de instituciones")
    })
    List<InstitutionResponse> getInstitutions();
}