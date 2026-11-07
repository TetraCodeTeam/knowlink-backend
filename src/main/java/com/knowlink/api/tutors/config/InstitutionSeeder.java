package com.knowlink.api.tutors.config;

import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(0)
@RequiredArgsConstructor
public class InstitutionSeeder implements CommandLineRunner {

    private static final List<String> INSTITUTIONS = List.of(
            InstitutionCatalogData.UTN_FRVM,
            InstitutionCatalogData.UNVM
    );

    private final IInstitutionRepository institutionRepository;

    @Override
    public void run(String... args) {
        INSTITUTIONS.forEach(name -> {
            if (institutionRepository.findByName(name).isPresent()) {
                return;
            }
            institutionRepository.save(Institution.builder().name(name).build());
        });
    }
}
