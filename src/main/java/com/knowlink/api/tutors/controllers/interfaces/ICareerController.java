package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.CareerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/careers")
@Tag(name = "Careers", description = "Catálogo de carreras")
public interface ICareerController {

    @GetMapping
    @Operation(summary = "Listar las carreras de una institución",
            description = "Devuelve las carreras regulares de la institución indicada, ordenadas por nombre. "
                    + "La carrera especial de materias compartidas nunca se incluye. "
                    + "Es un endpoint público: no requiere autenticación.")
    @ApiResponse(responseCode = "200", description = "Carreras ordenadas por nombre")
    @ApiResponse(responseCode = "400", description = "Falta el parámetro institutionId")
    @ApiResponse(responseCode = "404", description = "La institución no existe")
    @ResponseStatus(OK)
    List<CareerResponse> getAllCareers(
            @Parameter(description = "Institución de la que se listan las carreras") @RequestParam UUID institutionId);
}