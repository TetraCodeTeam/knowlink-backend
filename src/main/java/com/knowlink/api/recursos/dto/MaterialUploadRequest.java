package com.knowlink.api.recursos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MaterialUploadRequest(
        @NotBlank(message = "Debés indicar un nombre para el material")
        String name,

        @NotNull(message = "Debés asociar el material a una materia")
        UUID subjectId
) {}
