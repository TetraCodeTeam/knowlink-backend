package com.knowlink.api.catalog.services.implementations;

import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.catalog.services.interfaces.ICatalogCareerService;
import com.knowlink.api.catalog.services.interfaces.IInstitutionService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstitutionServiceImpl implements IInstitutionService {

    private final IInstitutionRepository institutionRepository;
    private final ICareerRepository careerRepository;

    @Override
    @Transactional
    public Institution createInstitution(String name) {
        if (institutionRepository.existsByName(name)) {
            throw new DuplicateResourceException(
                    "DUPLICATE_INSTITUTION",
                    "Ya existe una institución con ese nombre.",
                    String.format("institution con name '%s' ya existe", name));
        }

        Institution institution = institutionRepository.save(Institution.builder().name(name).build());

        careerRepository.save(Career.builder()
                .name(ICatalogCareerService.SHARED_CAREER_NAME)
                .institution(institution)
                .type(CareerType.COMPARTIDA)
                .build());

        return institution;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Institution> findAll() {
        return institutionRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Institution findOrThrow(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INSTITUTION_NOT_FOUND",
                        "Institución no encontrada.",
                        String.format("institution con id '%s' no existe", institutionId)));
    }
}