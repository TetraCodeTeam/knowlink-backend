package com.knowlink.api.tutors.config;

import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2) // runs after CareerSeeder
@RequiredArgsConstructor
public class SubjectSeeder implements CommandLineRunner {

    private final ISubjectRepository subjectRepository;
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

            Career sharedCareer = careerService.findOrCreateSharedCareer(institution);
            catalog.sharedSubjects().forEach(name -> saveIfAbsent(institution, sharedCareer, name));

            catalog.subjectsByCareer().forEach((careerName, subjectNames) -> {
                Career career = careerRepository
                        .findByInstitution_InstitutionIdAndName(institution.getInstitutionId(), careerName)
                        .orElseThrow(() -> new IllegalStateException(
                                "Career not found for subject seed: " + careerName + " (" + institutionName + ")"));
                subjectNames.forEach(name -> saveIfAbsent(institution, career, name));
            });
        });
    }

    private void saveIfAbsent(Institution institution, Career career, String name) {
        if (subjectRepository
                .findByInstitution_InstitutionIdAndName(institution.getInstitutionId(), name)
                .isPresent()) {
            return;
        }
        subjectRepository.save(Subject.builder()
                .name(name)
                .career(career)
                .institution(institution)
                .build());
    }
}
