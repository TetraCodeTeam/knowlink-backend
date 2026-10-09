package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.SubjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/subjects")
@Tag(name = "Subjects", description = "Catálogo de materias (DEPRECADO: usar /api/v1/catalog/subjects)")
public interface ISubjectController {

    @GetMapping("/basic")
    @Operation(deprecated = true, summary = "Materias basicas (deprecado)", description = "Endpoint deprecado: usar GET /api/v1/catalog/subjects.")
    List<SubjectResponse> getBasicSubjects();

    @GetMapping
    @Operation(deprecated = true, summary = "Materias por carrera (deprecado)", description = "Endpoint deprecado: usar GET /api/v1/catalog/subjects?institutionId=&careerId=.")
    List<SubjectResponse> getSubjectsByCareer(@RequestParam UUID careerId);
}