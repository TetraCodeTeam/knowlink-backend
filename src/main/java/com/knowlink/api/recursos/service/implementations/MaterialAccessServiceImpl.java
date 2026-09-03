package com.knowlink.api.recursos.service.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.recursos.dto.MaterialResponse;
import com.knowlink.api.recursos.exception.AccesoDenegadoException;
import com.knowlink.api.recursos.service.interfaces.IMaterialAccessService;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.tutors.data.models.AcademicMaterial;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.repositories.IAcademicMaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialAccessServiceImpl implements IMaterialAccessService {

    private final IBookingRepository bookingRepository;
    private final IAcademicMaterialRepository materialRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean tieneAcceso(UUID studentId, UUID tutorUserId) {
        List<UUID> completedSubjects = bookingRepository
                .findCompletedSubjectIdsByStudentAndTutor(studentId, tutorUserId);
        return !completedSubjects.isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialResponse> listarMaterialesAccesibles(UUID studentId, UUID tutorUserId, UUID subjectId) {
        List<UUID> completedSubjectIds = bookingRepository
                .findCompletedSubjectIdsByStudentAndTutor(studentId, tutorUserId);

        if (completedSubjectIds.isEmpty()) {
            return List.of();
        }

        List<UUID> subjectIdsToQuery;

        if (subjectId != null) {
            if (!completedSubjectIds.contains(subjectId)) {
                return List.of();
            }
            subjectIdsToQuery = List.of(subjectId);
        } else {
            subjectIdsToQuery = completedSubjectIds;
        }

        List<AcademicMaterial> materials = materialRepository
                .findAccessibleByTutorAndSubjectIds(tutorUserId, subjectIdsToQuery);

        return materials.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void validarAccesoODenegar(UUID studentId, UUID materialId) {
        AcademicMaterial material = materialRepository
                .findByAcademicMaterialIdAndActiveTrue(materialId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_NOT_FOUND",
                        "Material not found",
                        "Material not found with id: " + materialId));

        UUID tutorUserId = material.getTutorSubject().getTutorProfile().getUser().getUserId();
        UUID subjectId = material.getTutorSubject().getSubject().getSubjectId();

        List<UUID> completedSubjectIds = bookingRepository
                .findCompletedSubjectIdsByStudentAndTutor(studentId, tutorUserId);

        if (!completedSubjectIds.contains(subjectId)) {
            log.warn("Access denied: student {} tried to download material {} without completed session",
                    studentId, materialId);
            throw new AccesoDenegadoException("No tenés acceso a este material");
        }
    }

    private MaterialResponse toResponse(AcademicMaterial material) {
        Subject subject = material.getTutorSubject().getSubject();

        return new MaterialResponse(
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                subject.getSubjectId(),
                subject.getName(),
                material.getTutorSubject().getTutorProfile().getUser().getUserId(),
                material.getTutorSubject().getTutorProfile().getUser().getFullName(),
                material.getMaterialType().name(),
                null,
                material.getUploadedAt(),
                material.getSizeInBytes()
        );
    }
}
