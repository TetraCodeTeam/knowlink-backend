package com.knowlink.api.tutors.config;

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

    private static final List<String> CAREERS = List.of(
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

    @Override
    public void run(String... args) {
        if (careerRepository.count() > 0) {
            return;
        }
        CAREERS.forEach(name ->
                careerRepository.save(Career.builder().name(name).build())
        );
    }
}