package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.CareerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping("/api/v1/careers")
@Tag(name = "Careers", description = "Catálogo de carreras (DEPRECADO: usar /api/v1/catalog/institutions/{institutionId}/careers)")
public interface ICareerController {

    @GetMapping
    @Operation(deprecated = true, summary = "Listar carreras (deprecado)", description = "Endpoint deprecado: usar GET /api/v1/catalog/institutions/{institutionId}/careers para el catalogo multi-institucion.")
    List<CareerResponse> getAllCareers();
}