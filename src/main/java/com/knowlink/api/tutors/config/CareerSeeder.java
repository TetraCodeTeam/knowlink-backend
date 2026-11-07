package com.knowlink.api.tutors.config;

import com.knowlink.api.tutors.data.enums.CareerType;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class CareerSeeder implements CommandLineRunner {

    private final ICareerRepository careerRepository;
    private final IInstitutionRepository institutionRepository;
    private final ICareerService careerService;

    @Override
    public void run(String... args) {
        InstitutionCatalogData.catalog().forEach((institutionName, catalog) -> {
            Institution institution = institutionRepository.findByName(institutionName).orElse(null);
            if (institution == null) {
                return;
            }
            catalog.careers().forEach(careerName -> saveIfAbsent(institution, careerName));
            careerService.findOrCreateSharedCareer(institution);
        });
    }

    private void saveIfAbsent(Institution institution, String name) {
        if (careerRepository
                .findByInstitution_InstitutionIdAndName(institution.getInstitutionId(), name)
                .isPresent()) {
            return;
        }
        careerRepository.save(Career.builder()
                .institution(institution)
                .name(name)
                .type(CareerType.REGULAR)
                .build());
    }
}
