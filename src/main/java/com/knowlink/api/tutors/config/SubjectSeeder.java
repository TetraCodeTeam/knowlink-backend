package com.knowlink.api.tutors.config;

import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
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
import java.util.Set;

@Component
@Order(2)
@RequiredArgsConstructor
public class SubjectSeeder implements CommandLineRunner {

    private final ISubjectRepository subjectRepository;
    private final ICareerRepository careerRepository;
    private final IInstitutionRepository institutionRepository;

    private static final List<String> BASIC_SUBJECTS = List.of(
            "Análisis Matemático",
            "Álgebra",
            "Física",
            "Química",
            "Inglés"
    );

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
            ),
            "Ingeniería Química", List.of(
                    "Balances de Masa y Energía", "Ciencia de los Materiales",
                    "Fisicoquímica", "Procesos Biotecnológicos"
            )
    );

    private static final Map<String, List<String>> UNVM_CAREER_SUBJECTS = Map.of(
            "Profesorado de Matemática", List.of(
                    "Matemática Discreta", "Didáctica de la Matemática"
            ),
            "Tecnicatura en Desarrollo Web", List.of(
                    "Programación Web", "Bases de Datos Aplicadas"
            )
    );

    @Override
    public void run(String... args) {
        Institution utn = findInstitutionOrThrow("UTN FRVM");
        Institution unvm = findInstitutionOrThrow("UNVM");

        Career utnSharedCareer = findSharedCareerOrThrow(utn);
        BASIC_SUBJECTS.forEach(name -> saveIfAbsent(name, true, utn, Set.of(utnSharedCareer)));

        CAREER_SUBJECTS.forEach((careerName, subjectNames) -> {
            Career career = findCareerOrThrow(careerName, utn);
            subjectNames.forEach(name -> saveIfAbsent(name, false, utn, Set.of(career)));
        });

        Career unvmSharedCareer = findSharedCareerOrThrow(unvm);
        UNVM_CAREER_SUBJECTS.forEach((careerName, subjectNames) -> {
            Career career = findCareerOrThrow(careerName, unvm);
            subjectNames.forEach(name -> saveIfAbsent(name, false, unvm, Set.of(career)));
        });
        saveIfAbsent("Lenguaje y Redacción", true, unvm, Set.of(unvmSharedCareer));
    }

    private void saveIfAbsent(String name, boolean isBasic, Institution institution, Set<Career> careers) {
        if (subjectRepository.findByNameAndInstitutionInstitutionId(name, institution.getInstitutionId()).isPresent()) {
            return;
        }

        subjectRepository.save(Subject.builder()
                .name(name)
                .isBasic(isBasic)
                .institution(institution)
                .careers(careers)
                .build());
    }

    private Career findSharedCareerOrThrow(Institution institution) {
        return careerRepository.findByInstitutionInstitutionIdAndType(
                        institution.getInstitutionId(), CareerType.COMPARTIDA).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Carrera reservada no encontrada para la institución: " + institution.getName()));
    }

    private Career findCareerOrThrow(String careerName, Institution institution) {
        return careerRepository.findByInstitutionInstitutionId(institution.getInstitutionId()).stream()
                .filter(career -> career.getName().equals(careerName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Carrera no encontrada para seed de materias: " + careerName));
    }

    private Institution findInstitutionOrThrow(String name) {
        return institutionRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Institución no encontrada para seed de materias: " + name));
    }
}