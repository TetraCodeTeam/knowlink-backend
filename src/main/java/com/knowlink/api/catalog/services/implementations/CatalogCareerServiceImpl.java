package com.knowlink.api.catalog.services.implementations;

import com.knowlink.api.catalog.controllers.requests.CreateCareerRequest;
import com.knowlink.api.catalog.controllers.responses.CatalogCareerResponse;
import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.catalog.services.interfaces.ICatalogCareerService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogCareerServiceImpl implements ICatalogCareerService {

    private final ICareerRepository careerRepository;
    private final IInstitutionRepository institutionRepository;

    @Override
    @Transactional
    public CatalogCareerResponse createCareer(UUID institutionId, CreateCareerRequest request) {
        Institution institution = findInstitutionOrThrow(institutionId);

        if (request.type() == CareerType.COMPARTIDA) {
            throw new ValidationException("No es posible crear carreras de tipo compartido manualmente");
        }

        if (careerRepository.existsByInstitutionInstitutionIdAndName(institutionId, request.name())) {
            throw new DuplicateResourceException(
                    "DUPLICATE_CAREER",
                    "Ya existe una carrera con ese nombre en esta institución.",
                    String.format("career con name '%s' ya existe en institution '%s'", request.name(), institutionId));
        }

        Career career = careerRepository.save(Career.builder()
                .name(request.name())
                .institution(institution)
                .type(CareerType.REGULAR)
                .build());

        return toResponse(career);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogCareerResponse> findByInstitution(UUID institutionId) {
        findInstitutionOrThrow(institutionId);
        return careerRepository.findByInstitutionInstitutionId(institutionId).stream()
                .map(CatalogCareerServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Career findOrThrow(UUID careerId) {
        return careerRepository.findById(careerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "Carrera no encontrada.",
                        String.format("career con id '%s' no existe", careerId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Career findInInstitutionOrThrow(UUID institutionId, UUID careerId) {
        Career career = findOrThrow(careerId);
        if (!career.getInstitution().getInstitutionId().equals(institutionId)) {
            throw new ValidationException("La carrera seleccionada no pertenece a la institución indicada.");
        }
        return career;
    }

    @Override
    @Transactional(readOnly = true)
    public Career findSharedCareerOrThrow(UUID institutionId) {
        return careerRepository.findByInstitutionInstitutionIdAndType(institutionId, CareerType.COMPARTIDA).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SHARED_CAREER_NOT_FOUND",
                        "No se encontró la carrera de materias compartidas de la institución.",
                        String.format("shared career no existe en institution '%s'", institutionId)));
    }

    private Institution findInstitutionOrThrow(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INSTITUTION_NOT_FOUND",
                        "Institución no encontrada.",
                        String.format("institution con id '%s' no existe", institutionId)));
    }

    private static CatalogCareerResponse toResponse(Career career) {
        return new CatalogCareerResponse(career.getCareerId(), career.getName(), career.getType());
    }
}