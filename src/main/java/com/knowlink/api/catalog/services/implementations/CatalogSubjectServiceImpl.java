package com.knowlink.api.catalog.services.implementations;

import com.knowlink.api.catalog.controllers.responses.CatalogSubjectResponse;
import com.knowlink.api.catalog.data.enums.AssociationMode;
import com.knowlink.api.catalog.data.enums.CareerType;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.repositories.IInstitutionRepository;
import com.knowlink.api.catalog.services.interfaces.ICatalogSubjectService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicatedSubjectException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogSubjectServiceImpl implements ICatalogSubjectService {

    private final ISubjectRepository subjectRepository;
    private final ICareerRepository careerRepository;
    private final IInstitutionRepository institutionRepository;

    @Override
    @Transactional
    public CatalogSubjectResponse createSubject(UUID institutionId, String name, List<UUID> careerIds) {
        findInstitutionOrThrow(institutionId);
        List<Career> careers = findCareersInInstitution(institutionId, careerIds);

        if (subjectRepository.existsByInstitutionInstitutionIdAndName(institutionId, name)) {
            Subject existing = subjectRepository.findByNameAndInstitutionInstitutionId(name, institutionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Subject duplicated but not found for institution " + institutionId));
            throw new DuplicatedSubjectException(
                    "MATERIA_DUPLICADA",
                    "Ya existe una materia con ese nombre en esta institución. ¿Querés agregarle esta carrera?",
                    String.format("subject con name '%s' ya existe en institution '%s'", name, institutionId),
                    existing.getSubjectId());
        }

        Subject subject = subjectRepository.save(Subject.builder()
                .name(name)
                .isBasic(false)
                .institution(findInstitutionOrThrow(institutionId))
                .careers(new LinkedHashSet<>(careers))
                .build());

        return toResponse(subject);
    }

    @Override
    @Transactional
    public CatalogSubjectResponse updateSubjectCareers(UUID subjectId, List<UUID> careerIds, AssociationMode mode) {
        Subject subject = findSubjectOrThrow(subjectId);
        List<Career> careers = findCareersInInstitution(subject.getInstitution().getInstitutionId(), careerIds);

        if (mode == AssociationMode.ADD) {
            subject.getCareers().addAll(careers);
        } else {
            subject.setCareers(new LinkedHashSet<>(careers));
        }

        return toResponse(subjectRepository.save(subject));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogSubjectResponse> findByInstitution(UUID institutionId) {
        findInstitutionOrThrow(institutionId);
        return subjectRepository.findByInstitutionInstitutionId(institutionId).stream()
                .map(CatalogSubjectServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogSubjectResponse> findVisibleByInstitutionAndCareer(UUID institutionId, UUID careerId) {
        findInstitutionOrThrow(institutionId);
        findInstitutionCareerOrThrow(institutionId, careerId);
        return subjectRepository.findVisibleByInstitutionAndCareer(institutionId, careerId, CareerType.COMPARTIDA)
                .stream()
                .map(CatalogSubjectServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Subject findSubjectTeachableInCareerOrThrow(UUID subjectId, Career career) {
        Subject subject = findSubjectOrThrow(subjectId);
        UUID institutionId = career.getInstitution().getInstitutionId();

        if (!subject.getInstitution().getInstitutionId().equals(institutionId)) {
            throw new ValidationException("La materia seleccionada no pertenece a la institución de la carrera elegida.");
        }

        boolean teachable = subject.getCareers().stream().anyMatch(associated -> {
            if (associated.getCareerId().equals(career.getCareerId())) {
                return true;
            }
            return associated.getType() == CareerType.COMPARTIDA
                    && associated.getInstitution().getInstitutionId().equals(institutionId);
        });

        if (!teachable) {
            throw new ValidationException(
                    "La materia seleccionada no está asociada a esta carrera ni a las materias compartidas de la institución.");
        }

        return subject;
    }

    private List<Career> findCareersInInstitution(UUID institutionId, List<UUID> careerIds) {
        List<Career> careers = careerRepository.findAllById(careerIds);

        if (careers.size() != Set.copyOf(careerIds).size()) {
            throw new ValidationException("Una o más de las carreras indicadas no existen.");
        }

        boolean outsideInstitution = careers.stream()
                .anyMatch(career -> !career.getInstitution().getInstitutionId().equals(institutionId));
        if (outsideInstitution) {
            throw new ValidationException("Solo podés asociar materias a carreras de la misma institución.");
        }

        return careers;
    }

    private Career findInstitutionCareerOrThrow(UUID institutionId, UUID careerId) {
        Career career = careerRepository.findById(careerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "Carrera no encontrada.",
                        String.format("career con id '%s' no existe", careerId)));
        if (!career.getInstitution().getInstitutionId().equals(institutionId)) {
            throw new ValidationException("La carrera indicada no pertenece a la institución indicada.");
        }
        return career;
    }

    private Subject findSubjectOrThrow(UUID subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_NOT_FOUND",
                        "Materia no encontrada.",
                        String.format("subject con id '%s' no existe", subjectId)));
    }

    private Institution findInstitutionOrThrow(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INSTITUTION_NOT_FOUND",
                        "Institución no encontrada.",
                        String.format("institution con id '%s' no existe", institutionId)));
    }

    private static CatalogSubjectResponse toResponse(Subject subject) {
        List<UUID> careerIds = subject.getCareers().stream()
                .map(Career::getCareerId)
                .toList();
        return new CatalogSubjectResponse(subject.getSubjectId(), subject.getName(), subject.isBasic(), careerIds);
    }
}