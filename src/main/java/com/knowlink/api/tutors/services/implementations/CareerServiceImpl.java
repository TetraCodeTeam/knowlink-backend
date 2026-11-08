package com.knowlink.api.tutors.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.enums.CareerType;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import com.knowlink.api.tutors.services.interfaces.IInstitutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CareerServiceImpl implements ICareerService {

    static final String SHARED_CAREER_NAME = "Materias Compartidas";

    private final ICareerRepository careerRepository;
    private final IInstitutionService institutionService;

    @Override
    @Transactional(readOnly = true)
    public Career findRegularByNameAndInstitutionOrThrowException(String name, UUID institutionId) {
        return careerRepository.findByInstitution_InstitutionIdAndName(institutionId, name)
                .filter(career -> career.getType() == CareerType.REGULAR)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "Carrera no encontrada.",
                        String.format("career con name '%s' no existe en la institución '%s'", name, institutionId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Career> findRegularByInstitution(UUID institutionId) {
        institutionService.findByIdOrThrowException(institutionId);
        return careerRepository.findByInstitution_InstitutionIdAndTypeOrderByName(institutionId, CareerType.REGULAR);
    }

    @Override
    @Transactional
    public Career findOrCreateSharedCareer(Institution institution) {
        UUID institutionId = institution.getInstitutionId();
        Optional<Career> sharedCareer = careerRepository
                .findByInstitution_InstitutionIdAndType(institutionId, CareerType.SHARED);

        if (sharedCareer.isEmpty()) {
            // Databases migrated from the legacy COMPARTIDA enum store the row
            // under the canonical name but with an unknown type; reuse it instead
            // of colliding with uk_career_institution_name.
            Optional<Career> legacyCareer = careerRepository
                    .findByInstitution_InstitutionIdAndName(institutionId, SHARED_CAREER_NAME);
            if (legacyCareer.isPresent()) {
                Career legacy = legacyCareer.get();
                legacy.setType(CareerType.SHARED);
                return careerRepository.save(legacy);
            }
        }

        return sharedCareer.orElseGet(() -> careerRepository.save(Career.builder()
                .institution(institution)
                .name(SHARED_CAREER_NAME)
                .type(CareerType.SHARED)
                .build()));
    }
}