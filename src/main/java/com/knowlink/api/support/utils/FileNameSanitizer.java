package com.knowlink.api.support.utils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds object keys that Supabase Storage accepts.
 *
 * <p>Supabase rejects keys containing spaces, accents or other symbols with an
 * {@code InvalidKey} error, so user provided file names must be sanitized before they
 * become part of a storage path. Callers keep the original file name in their own
 * metadata for display.</p>
 */
public final class FileNameSanitizer {

    private static final int MAX_SAFE_BASE_LENGTH = 100;
    private static final String FALLBACK_BASE = "archivo";

    private FileNameSanitizer() {
    }

    /**
     * "Logotipo UTN Villa Maria en azul.png" -> "Logotipo_UTN_Villa_Maria_en_azul.png".
     */
    public static String toSafeFileName(String originalFileName) {
        String name = originalFileName == null ? "" : originalFileName;
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot + 1);

        String safeBase = sanitize(base);
        if (safeBase.length() > MAX_SAFE_BASE_LENGTH) {
            safeBase = safeBase.substring(0, MAX_SAFE_BASE_LENGTH);
        }
        if (safeBase.isEmpty()) {
            safeBase = FALLBACK_BASE;
        }
        String safeExtension = sanitize(extension).toLowerCase(Locale.ROOT);
        return safeExtension.isEmpty() ? safeBase : safeBase + "." + safeExtension;
    }

    private static String sanitize(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
    }
}