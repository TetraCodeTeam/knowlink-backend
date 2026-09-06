package com.knowlink.api.resources.service.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.resources.dto.MaterialResponse;
import com.knowlink.api.resources.dto.MaterialUploadRequest;
import com.knowlink.api.resources.exception.FormatNotAllowedException;
import com.knowlink.api.resources.exception.NoActiveReservationException;
import com.knowlink.api.resources.exception.SubjectNotAssociatedException;
import com.knowlink.api.resources.service.interfaces.IMaterialAccessService;
import com.knowlink.api.resources.service.interfaces.IMaterialService;
import com.knowlink.api.resources.service.interfaces.IReservationService;
import com.knowlink.api.resources.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.data.enums.MaterialType;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.AcademicMaterial;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.IAcademicMaterialRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialServiceImpl implements IMaterialService {

    private static final Map<String, Set<String>> ALLOWED_FORMATS = Map.of(
            "PNG", Set.of("image/png"),
            "PDF", Set.of("application/pdf"),
            "XLSX", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "pdf", "xlsx");

    private final IAcademicMaterialRepository materialRepository;
    private final ISubjectRepository subjectRepository;
    private final ITutorProfileRepository tutorProfileRepository;
    private final ITutorSubjectRepository tutorSubjectRepository;
    private final ISupabaseStorageService supabaseStorageService;
    private final IReservationService reservationService;
    private final IMaterialAccessService materialAccessService;

    @Override
    @Transactional
    public MaterialResponse upload(MaterialUploadRequest request, MultipartFile file, UUID tutorUserId) {
        validateSubjectId(request.subjectId());
        validateName(request.name());
        validateFormat(file);

        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_NOT_FOUND",
                        "Subject not found",
                        "Subject not found with id: " + request.subjectId()));

        TutorProfile tutorProfile = tutorProfileRepository.findByUserId(tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TUTOR_PROFILE_NOT_FOUND",
                        "Tutor profile not found",
                        "Tutor profile not found for user: " + tutorUserId));

        TutorSubject tutorSubject = findOrCreateTutorSubject(tutorProfile, subject);

        String storagePath = supabaseStorageService.upload(file, request.subjectId());

        MaterialType materialType = resolveMaterialType(file);

        AcademicMaterial material = AcademicMaterial.builder()
                .tutorSubject(tutorSubject)
                .name(request.name())
                .originalFileName(file.getOriginalFilename())
                .storagePath(storagePath)
                .uploadedAt(LocalDateTime.now())
                .sizeInBytes(file.getSize())
                .reportsCount(0)
                .available(true)
                .active(true)
                .materialType(materialType)
                .build();

        material = materialRepository.save(material);
        log.info("Material uploaded: {} by tutor: {}", material.getName(), tutorUserId);

        return toResponse(material);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialResponse> listBySubject(UUID subjectId, UUID userId, String role) {
        List<AcademicMaterial> materials = materialRepository.findActiveBySubjectId(subjectId);

        if (role.equals(Role.STUDENT.name())) {
            if (!reservationService.hasAnyReservationForSubject(userId, subjectId)) {
                throw new NoActiveReservationException("El alumno no tiene reserva activa con este tutor");
            }

            materials = materials.stream()
                    .filter(m -> {
                        UUID tutorUserId = m.getTutorSubject().getTutorProfile().getUser().getUserId();
                        return reservationService.hasReservationWithTutorForSubject(userId, tutorUserId, subjectId);
                    })
                    .toList();
        } else if (role.equals(Role.TUTOR.name())) {
            // Un tutor solo ve el material que él mismo cargó para esta materia,
            // no el de otros tutores que también la dictan.
            materials = materials.stream()
                    .filter(m -> m.getTutorSubject().getTutorProfile().getUser().getUserId().equals(userId))
                    .toList();
        }

        return materials.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID materialId, UUID userId, String role) {
        AcademicMaterial material = materialRepository.findByAcademicMaterialIdAndActiveTrue(materialId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_NOT_FOUND",
                        "Material not found",
                        "Material not found with id: " + materialId));

        UUID subjectId = material.getTutorSubject().getSubject().getSubjectId();
        UUID tutorUserId = material.getTutorSubject().getTutorProfile().getUser().getUserId();

        if (role.equals(Role.STUDENT.name())) {
            materialAccessService.validateAccessOrDeny(userId, materialId);
        }

        return supabaseStorageService.generateSignedUrl(material.getStoragePath(), 3600);
    }

    private void validateSubjectId(UUID subjectId) {
        if (subjectId == null) {
            throw new SubjectNotAssociatedException("Debés asociar el material a una materia");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Debés indicar un nombre para el material");
        }
    }

    private void validateFormat(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isContentTypeAllowed(extension, contentType)) {
            throw new FormatNotAllowedException("El formato del archivo no está permitido");
        }
    }

    private boolean isContentTypeAllowed(String extension, String contentType) {
        Set<String> allowedContentTypes = ALLOWED_FORMATS.get(extension.toUpperCase());
        return allowedContentTypes != null && allowedContentTypes.contains(contentType);
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    private MaterialType resolveMaterialType(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename()).toUpperCase();
        return MaterialType.valueOf(extension);
    }

    private TutorSubject findOrCreateTutorSubject(TutorProfile tutorProfile, Subject subject) {
        List<TutorSubject> tutorSubjects = tutorSubjectRepository.findByTutorProfileId(tutorProfile.getTutorProfileId());
        return tutorSubjects.stream()
                .filter(ts -> ts.getSubject().getSubjectId().equals(subject.getSubjectId()))
                .findFirst()
                .orElseGet(() -> {
                    TutorSubject newTutorSubject = TutorSubject.builder()
                            .tutorProfile(tutorProfile)
                            .subject(subject)
                            .compensationType(CompensationType.PAID)
                            .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                            .modality(Modality.VIRTUAL)
                            .build();
                    return tutorSubjectRepository.save(newTutorSubject);
                });
    }

    private MaterialResponse toResponse(AcademicMaterial material) {
        Subject subject = material.getTutorSubject().getSubject();
        TutorProfile tutorProfile = material.getTutorSubject().getTutorProfile();

        return new MaterialResponse(
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                subject.getSubjectId(),
                subject.getName(),
                tutorProfile.getUser().getUserId(),
                tutorProfile.getUser().getFullName(),
                material.getMaterialType().name(),
                null,
                material.getUploadedAt(),
                material.getSizeInBytes()
        );
    }
}