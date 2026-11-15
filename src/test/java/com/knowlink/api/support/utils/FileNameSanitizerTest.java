package com.knowlink.api.support.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameSanitizerTest {

    @Test
    @DisplayName("Accents, spaces and symbols are removed so Supabase accepts the object key")
    void toSafeFileName_withAccentsAndSpaces_sanitizesName() {
        assertThat(FileNameSanitizer.toSafeFileName("Apuntes de Física 1.pdf"))
                .isEqualTo("Apuntes_de_Fisica_1.pdf");
    }

    @Test
    @DisplayName("Underscores and hyphens are preserved")
    void toSafeFileName_keepsUnderscoresAndHyphens() {
        assertThat(FileNameSanitizer.toSafeFileName("guia-teorica_2.png")).isEqualTo("guia-teorica_2.png");
    }

    @Test
    @DisplayName("Extension is lowercased")
    void toSafeFileName_lowercasesExtension() {
        assertThat(FileNameSanitizer.toSafeFileName("Resumen.PDF")).isEqualTo("Resumen.pdf");
    }

    @Test
    @DisplayName("Path separators do not create extra folders inside the storage key")
    void toSafeFileName_replacesPathSeparators() {
        assertThat(FileNameSanitizer.toSafeFileName("temas/parcial 1.pdf")).isEqualTo("temas_parcial_1.pdf");
    }

    @Test
    @DisplayName("Missing or unusable names fall back to a safe base name")
    void toSafeFileName_unusableName_fallsBackToArchivo() {
        assertThat(FileNameSanitizer.toSafeFileName(null)).isEqualTo("archivo");
        assertThat(FileNameSanitizer.toSafeFileName("")).isEqualTo("archivo");
        assertThat(FileNameSanitizer.toSafeFileName("###.png")).isEqualTo("archivo.png");
    }

    @Test
    @DisplayName("Name without extension keeps the sanitized base")
    void toSafeFileName_withoutExtension_keepsBase() {
        assertThat(FileNameSanitizer.toSafeFileName("resumen final")).isEqualTo("resumen_final");
    }

    @Test
    @DisplayName("Long base names are truncated to a safe length")
    void toSafeFileName_longName_isTruncated() {
        String longName = "a".repeat(150) + ".pdf";

        assertThat(FileNameSanitizer.toSafeFileName(longName)).isEqualTo("a".repeat(100) + ".pdf");
    }
}