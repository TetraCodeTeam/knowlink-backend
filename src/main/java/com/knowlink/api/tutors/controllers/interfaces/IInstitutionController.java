package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.InstitutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RequestMapping("/api/v1/institutions")
@Tag(name = "Institutions", description = "Catálogo de instituciones")
public interface IInstitutionController {

    @GetMapping
    @Operation(summary = "Listar las instituciones educativas",
            description = "Devuelve todas las instituciones disponibles, ordenadas alfabéticamente por nombre. "
                    + "Es un endpoint público: no requiere autenticación.")
    @ApiResponse(responseCode = "200", description = "Instituciones ordenadas por nombre")
    @ResponseStatus(OK)
    List<InstitutionResponse> getAllInstitutions();
}