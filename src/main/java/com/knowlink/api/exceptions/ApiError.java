package com.knowlink.api.exceptions;

public record ApiError(
        int status,
        String message,
        String detail
) {}
