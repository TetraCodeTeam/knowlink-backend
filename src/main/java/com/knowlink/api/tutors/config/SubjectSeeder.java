// SubjectSeeder.java
package com.knowlink.api.tutors.config;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(2) // corre después de CareerSeeder
@RequiredArgsConstructor
public class SubjectSeeder implements CommandLineRunner {

    private final ISubjectRepository subjectRepository;
    private final ICareerRepository careerRepository;

    // Materias básicas: no dependen de una carrera puntual, pero el modelo
    // actual exige una FK -> se asocian a una carrera "placeholder" solo para
    // satisfacer la constraint. El front las filtra por isBasic, no por carrera.
    private static final List<String> BASIC_SUBJECTS = List.of(
            "Análisis Matemático",
            "Álgebra",
            "Física",
            "Química",
            "Inglés"
    );

    // Nombres únicos entre carreras (restricción actual: Subject.name es unique global)
    private static final Map<String, List<String>> CAREER_SUBJECTS = Map.of(
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
            )
    );

    @Override
    public void run(String... args) {
        if (subjectRepository.count() > 0) {
            return;
        }

        Career placeholderCareer = careerRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay carreras cargadas: CareerSeeder debe correr antes que SubjectSeeder"));

        BASIC_SUBJECTS.forEach(name ->
                subjectRepository.save(Subject.builder()
                        .name(name)
                        .isBasic(true)
                        .career(placeholderCareer)
                        .build())
        );

        CAREER_SUBJECTS.forEach((careerName, subjectNames) -> {
            Career career = careerRepository.findByName(careerName)
                    .orElseThrow(() -> new IllegalStateException(
                            "Carrera no encontrada para seed de materias: " + careerName));

            subjectNames.forEach(name ->
                    subjectRepository.save(Subject.builder()
                            .name(name)
                            .isBasic(false)
                            .career(career)
                            .build())
            );
        });
    }
}