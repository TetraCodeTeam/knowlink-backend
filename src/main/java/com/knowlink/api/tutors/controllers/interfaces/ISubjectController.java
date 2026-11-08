package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.SubjectResponse;
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

@RequestMapping("/api/v1/subjects")
@Tag(name = "Subjects", description = "Catálogo de materias")
public interface ISubjectController {

    @GetMapping("/basic")
    @Operation(summary = "Listar las materias compartidas de una institución",
            description = "Devuelve las materias transversales de la institución (las que pertenecen a su carrera "
                    + "compartida), ordenadas por nombre. Es un endpoint público: no requiere autenticación.")
    @ApiResponse(responseCode = "200", description = "Materias compartidas ordenadas por nombre")
    @ApiResponse(responseCode = "400", description = "Falta el parámetro institutionId")
    @ResponseStatus(OK)
    List<SubjectResponse> getBasicSubjects(
            @Parameter(description = "Institución de la que se listan las materias compartidas") @RequestParam UUID institutionId);

    @GetMapping
    @Operation(summary = "Listar las materias propias de una carrera",
            description = "Devuelve las materias que pertenecen a la carrera indicada, ordenadas por nombre. "
                    + "Es un endpoint público: no requiere autenticación.")
    @ApiResponse(responseCode = "200", description = "Materias de la carrera ordenadas por nombre")
    @ApiResponse(responseCode = "400", description = "Falta el parámetro careerId")
    @ResponseStatus(OK)
    List<SubjectResponse> getSubjectsByCareer(
            @Parameter(description = "Carrera de la que se listan las materias") @RequestParam UUID careerId);
}