package com.knowlink.api.catalog.config;

import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.catalog.services.interfaces.IInstitutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(0)
@RequiredArgsConstructor
public class InstitutionSeeder implements CommandLineRunner {

    private final IInstitutionRepository institutionRepository;
    private final IInstitutionService institutionService;

    private static final List<String> INSTITUTIONS = List.of(
            "UTN FRVM",
            "UNVM"
    );

    @Override
    public void run(String... args) {
        INSTITUTIONS.forEach(name -> {
            if (institutionRepository.findByName(name).isEmpty()) {
                institutionService.createInstitution(name);
            }
        });
    }
}