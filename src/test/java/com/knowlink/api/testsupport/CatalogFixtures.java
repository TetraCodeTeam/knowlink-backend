package com.knowlink.api.testsupport;

import com.knowlink.api.tutors.data.enums.CareerType;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;

/**
 * Shared fixtures for the multi-institution catalog. Every persisted Career or
 * Subject needs a non-null Institution, so tests build their catalogs through
 * these helpers instead of repeating the wiring.
 */
public final class CatalogFixtures {

    public static final String DEFAULT_INSTITUTION = "UTN FRVM";
    public static final String SHARED_CAREER_NAME = "Materias Compartidas";

    private CatalogFixtures() {
    }

    public static Institution institution(String name) {
        return Institution.builder().name(name).build();
    }

    public static Institution institution() {
        return institution(DEFAULT_INSTITUTION);
    }

    public static Career career(Institution institution, String name) {
        return Career.builder()
                .name(name)
                .institution(institution)
                .type(CareerType.REGULAR)
                .build();
    }

    public static Career sharedCareer(Institution institution) {
        return Career.builder()
                .name(SHARED_CAREER_NAME)
                .institution(institution)
                .type(CareerType.SHARED)
                .build();
    }

    public static Subject subject(Career career, String name) {
        return Subject.builder()
                .name(name)
                .career(career)
                .institution(career.getInstitution())
                .build();
    }

    public static Institution saveInstitution(IInstitutionRepository repository, String name) {
        return repository.findByName(name)
                .orElseGet(() -> repository.save(institution(name)));
    }

    public static Career saveCareer(ICareerRepository repository,
                                    IInstitutionRepository institutionRepository,
                                    String name) {
        Institution institution = saveInstitution(institutionRepository, DEFAULT_INSTITUTION);
        return repository.findByInstitution_InstitutionIdAndName(institution.getInstitutionId(), name)
                .orElseGet(() -> repository.save(career(institution, name)));
    }

    public static Career saveCareer(ICareerRepository repository,
                                    Institution institution,
                                    String name) {
        return repository.findByInstitution_InstitutionIdAndName(institution.getInstitutionId(), name)
                .orElseGet(() -> repository.save(career(institution, name)));
    }

    public static Career saveSharedCareer(ICareerRepository repository, Institution institution) {
        return repository.findByInstitution_InstitutionIdAndType(institution.getInstitutionId(), CareerType.SHARED)
                .orElseGet(() -> repository.save(sharedCareer(institution)));
    }

    public static Subject saveSubject(ISubjectRepository repository, Career career, String name) {
        return repository.findByInstitution_InstitutionIdAndName(
                        career.getInstitution().getInstitutionId(), name)
                .orElseGet(() -> repository.save(subject(career, name)));
    }
}