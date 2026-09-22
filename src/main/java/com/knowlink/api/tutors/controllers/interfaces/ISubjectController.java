package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.SubjectResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/subjects")
@Tag(name = "Subjects", description = "Catálogo de materias")
public interface ISubjectController {

    @GetMapping("/basic")
    List<SubjectResponse> getBasicSubjects();

    @GetMapping
    List<SubjectResponse> getSubjectsByCareer(@RequestParam UUID careerId);
}