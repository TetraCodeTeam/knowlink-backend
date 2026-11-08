package com.knowlink.api.tutors.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog loaded by the seeders for the two institutions supported by the MVP.
 * UTN FRVM keeps the original seed data; UNVM is a minimal catalog used to
 * validate the multi-institution behaviour.
 */
public final class InstitutionCatalogData {

    public static final String UTN_FRVM = "UTN FRVM";
    public static final String UNVM = "UNVM";
    public static final String SHARED_CAREER_NAME = "Materias Compartidas";

    private static final List<String> UTN_CAREERS = List.of(
            "Ingeniería en Sistemas",
            "Ingeniería Civil",
            "Ingeniería Mecánica",
            "Ingeniería Electrónica",
            "Ingeniería Industrial",
            "Ingeniería Química",
            "Ingeniería Eléctrica",
            "Ciencias de la Computación",
            "Licenciatura en Administración",
            "Contador Público",
            "Medicina",
            "Enfermería",
            "Derecho",
            "Arquitectura",
            "Diseño Gráfico",
            "Psicología",
            "Comunicación Social",
            "Matemática",
            "Física",
            "Química",
            "Otra"
    );

    private static final List<String> UTN_SHARED_SUBJECTS = List.of(
            "Análisis Matemático",
            "Álgebra",
            "Física",
            "Química",
            "Inglés"
    );

    private static final Map<String, List<String>> UTN_SUBJECTS_BY_CAREER = Map.of(
            "Ingeniería en Sistemas", List.of(
                    "Programación", "Estructuras de Datos", "Base de Datos", "Redes",
                    "Sistemas Operativos", "Algoritmos", "Ingeniería de Software",
                    "Arquitectura de Computadoras", "Inteligencia Artificial", "Machine Learning"
            ),
            "Ciencias de la Computación", List.of(
                    "Teoría de la Computación", "Compiladores", "Sistemas Distribuidos"
            ),
            "Ingeniería Civil", List.of(
                    "Estática", "Resistencia de Materiales", "Topografía", "Hormigón Armado"
            ),
            "Licenciatura en Administración", List.of(
                    "Contabilidad", "Economía", "Gestión de Proyectos", "Enfoque Lean Ágil"
            ),
            "Contador Público", List.of(
                    "Auditoría", "Costos", "Finanzas Corporativas"
            ),
            "Ingeniería Química", List.of(
                    "Balances de Masa y Energía", "Ciencia de los Materiales",
                    "Fisicoquímica", "Procesos Biotecnológicos"
            )
    );

    private static final List<String> UNVM_CAREERS = List.of(
            "Licenciatura en Administración",
            "Medicina Veterinaria"
    );

    private static final List<String> UNVM_SHARED_SUBJECTS = List.of(
            "Análisis Matemático",
            "Inglés"
    );

    private static final Map<String, List<String>> UNVM_SUBJECTS_BY_CAREER = Map.of(
            "Licenciatura en Administración", List.of(
                    "Contabilidad", "Economía", "Derecho Administrativo"
            ),
            "Medicina Veterinaria", List.of(
                    "Anatomía Veterinaria", "Fisiología Animal", "Farmacología Veterinaria"
            )
    );

    private static final Map<String, InstitutionCatalog> CATALOG = buildCatalog();

    private InstitutionCatalogData() {
    }

    public static Map<String, InstitutionCatalog> catalog() {
        return CATALOG;
    }

    private static Map<String, InstitutionCatalog> buildCatalog() {
        Map<String, InstitutionCatalog> catalog = new LinkedHashMap<>();
        catalog.put(UTN_FRVM, new InstitutionCatalog(UTN_CAREERS, UTN_SHARED_SUBJECTS, UTN_SUBJECTS_BY_CAREER));
        catalog.put(UNVM, new InstitutionCatalog(UNVM_CAREERS, UNVM_SHARED_SUBJECTS, UNVM_SUBJECTS_BY_CAREER));
        return Map.copyOf(catalog);
    }
}