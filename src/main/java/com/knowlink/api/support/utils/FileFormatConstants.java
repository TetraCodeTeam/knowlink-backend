package com.knowlink.api.support.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FileFormatConstants {

    private FileFormatConstants() {}

    public static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    public static final Set<String> ALLOWED_EXTENSIONS = ALLOWED_CONTENT_TYPES.keySet();
    public static final List<String> ACCEPTED_FORMAT_LABELS = ALLOWED_EXTENSIONS.stream()
            .map(String::toUpperCase)
            .sorted()
            .toList();
}
