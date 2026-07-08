package com.knowlink.api.tutors.controllers.responses;

import java.util.UUID;

public record SubjectResponse(
    UUID subjectId, 
    String name, 
    boolean isBasic
) {}