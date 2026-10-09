package com.knowlink.api.catalog.controllers.responses;

import java.util.UUID;

public record SubjectDuplicatedErrorResponse(String error, String message, UUID materiaExistenteId) {}