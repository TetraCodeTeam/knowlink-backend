package com.knowlink.api.tutors.config;

import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
public class CareerSeeder implements CommandLineRunner {

    private final ICareerRepository careerRepository;
    private final IInstitutionRepository institutionRepository;

    private static final List<String> UTN_FRVM_CAREERS = List.of(
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

    private static final List<String> UNVM_CAREERS = List.of(
            "Profesorado de Matemática",
            "Tecnicatura en Desarrollo Web"
    );

    @Override
    public void run(String... args) {
        Institution utn = findInstitutionOrThrow("UTN FRVM");
        Institution unvm = findInstitutionOrThrow("UNVM");

        UTN_FRVM_CAREERS.forEach(name -> saveIfAbsent(name, utn));
        UNVM_CAREERS.forEach(name -> saveIfAbsent(name, unvm));
    }

    private void saveIfAbsent(String name, Institution institution) {
        if (careerRepository.existsByInstitutionInstitutionIdAndName(institution.getInstitutionId(), name)) {
            return;
        }
        careerRepository.save(Career.builder().name(name).institution(institution).build());
    }

    private Institution findInstitutionOrThrow(String name) {
        return institutionRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Institución no encontrada para seed de carreras: " + name));
    }
}