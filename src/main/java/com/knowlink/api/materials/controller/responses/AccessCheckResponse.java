package com.knowlink.api.materials.controller.responses;

import java.util.List;

public record AccessCheckResponse(boolean accessEnabled, List<String> accessibleSubjects) {}
