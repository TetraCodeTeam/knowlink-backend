package com.knowlink.api.students.controllers.requests;

import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import jakarta.validation.Valid;

import java.util.List;

public record ActivateTutorRoleRequest(
        String biography,
        String address,
        // requerido solo en la primera activación; null/vacío es válido para el toggle
        @Valid List<TutorSubjectRequest> subjects
) {}
