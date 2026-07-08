package com.knowlink.api.tutors.controllers.interfaces;

import com.knowlink.api.tutors.controllers.responses.CareerResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping("/api/v1/careers")
@Tag(name = "Careers", description = "Catálogo de carreras")
public interface ICareerController {

    @GetMapping
    List<CareerResponse> getAllCareers();
}