package com.knowlink.api.tutors.controllers.responses;

import java.util.UUID;

public record CareerResponse(
    UUID careerId, 
    String name
) {}