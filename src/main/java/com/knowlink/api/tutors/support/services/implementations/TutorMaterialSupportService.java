package com.knowlink.api.tutors.support.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialDefense;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.data.models.MaterialDefenseAttachment;
import com.knowlink.api.materials.repositories.IAcademicMaterialDefenseRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.MaterialDefenseAttachmentRepository;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.materials.utils.StorageConstants;
import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialResponse;
import com.knowlink.api.tutors.support.data.mappers.TutorSupportMapper;
import com.knowlink.api.materials.policy.MaterialReportingPolicyProvider;
import com.knowlink.api.tutors.support.services.interfaces.ITutorMaterialSupportService;
import com.knowlink.api.tutors.support.validations.MaterialDefenseValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TutorMaterialSupportService implements ITutorMaterialSupportService {

    private final IAcademicMaterialReportRepository materialReportRepository;
    private final IAcademicMaterialRepository materialRepository;
    private final IAcademicMaterialDefenseRepository materialDefenseRepository;
    private final MaterialDefenseAttachmentRepository defenseAttachmentRepository;
    private final ISupabaseStorageService storageService;
    private final TutorSupportMapper tutorSupportMapper;
    private final MaterialDefenseValidator materialDefenseValidator;
    private final MaterialReportingPolicyProvider materialReportingPolicyProvider;

    @Override
    @Transactional(readOnly = true)
    public List<TutorReportedMaterialResponse> getReportedMaterials(UUID tutorUserId) {
        List<AcademicMaterialReport> reports = materialReportRepository
                .findByMaterial_TutorSubject_TutorProfile_User_UserIdOrderByReportedAtDesc(tutorUserId);
        return tutorSupportMapper.toReportedMaterials(reports);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialReportingPolicyResponse getMaterialReportingPolicy() {
        return materialReportingPolicyProvider.getPolicy();
    }

    @Override
    @Transactional(readOnly = true)
    public TutorReportedMaterialDetailResponse getReportedMaterialDetails(UUID tutorUserId, UUID materialId) {
        AcademicMaterial material = findReportedMaterialOrThrow(tutorUserId, materialId);
        return loadReportedMaterialDetails(materialId, material);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorReportedMaterialDetailResponse getAdminReportedMaterialDetails(UUID materialId) {
        AcademicMaterial material = materialRepository.findByAcademicMaterialIdAndActiveTrue(materialId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REPORTED_MATERIAL_NOT_FOUND",
                        "El material denunciado no existe.",
                        "Reported material not found for admin: " + materialId));
        return loadReportedMaterialDetails(materialId, material);
    }

    @Override
    @Transactional
    public TutorReportedMaterialDetailResponse submitMaterialDefense(
            UUID tutorUserId,
            UUID materialId,
            SubmitMaterialDefenseRequest request,
            List<MultipartFile> files) {
        AcademicMaterial material = findReportedMaterialOrThrow(tutorUserId, materialId);
        List<MultipartFile> attachments = materialDefenseValidator.normalizeAndValidate(request, files);

        List<AcademicMaterialReport> reports = findReportsOrThrow(materialId);
        boolean unresolvedReports = reports.stream()
                .anyMatch(report -> MaterialReportStatus.orPending(report.getStatus())
                        != MaterialReportStatus.RESOLVED);
        if (!unresolvedReports) {
            throw new DuplicateResourceException(
                    "MATERIAL_REVIEW_COMPLETED",
                    "La revisión de este material ya finalizó.",
                    "Cannot submit defense after all reports are resolved for material " + materialId);
        }
        var mostRecentReportAt = reports.get(0).getReportedAt();
        AcademicMaterialDefense latestDefense = materialDefenseRepository
                .findFirstByMaterial_AcademicMaterialIdOrderBySubmittedAtDesc(materialId)
                .orElse(null);
        if (latestDefense != null && !latestDefense.getSubmittedAt().isBefore(mostRecentReportAt)) {
            throw new DuplicateResourceException(
                    "MATERIAL_DEFENSE_ALREADY_SUBMITTED",
                    "Ya presentaste un descargo para este material.",
                    "Material defense already exists for material " + materialId);
        }

        AcademicMaterialDefense defense = materialDefenseRepository.saveAndFlush(AcademicMaterialDefense.builder()
                .material(material)
                .tutor(material.getTutorSubject().getTutorProfile().getUser())
                .description(request.description())
                .build());

        List<String> uploadedPaths = new ArrayList<>();
        try {
            List<MaterialDefenseAttachment> entities = new ArrayList<>();
            for (MultipartFile file : attachments) {
                String path = storageService.upload(file, "material-defense/" + materialId);
                uploadedPaths.add(path);
                entities.add(MaterialDefenseAttachment.builder()
                        .defense(defense)
                        .storagePath(path)
                        .originalFileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .sizeInBytes(file.getSize())
                        .build());
            }
            defenseAttachmentRepository.saveAll(entities);
        } catch (RuntimeException exception) {
            cleanupUploads(uploadedPaths);
            throw exception;
        }

        return getReportedMaterialDetails(tutorUserId, materialId);
    }

    private TutorReportedMaterialDetailResponse loadReportedMaterialDetails(
            UUID materialId,
            AcademicMaterial material) {
        List<AcademicMaterialReport> reports = findReportsOrThrow(materialId);
        AcademicMaterialDefense defense = materialDefenseRepository
                .findFirstByMaterial_AcademicMaterialIdOrderBySubmittedAtDesc(materialId)
                .orElse(null);
        List<MaterialDefenseAttachment> attachments = defense == null
                ? List.of()
                : defenseAttachmentRepository.findByDefense_DefenseId(defense.getDefenseId());
        List<TutorReportedMaterialDetailResponse.DefenseAttachment> defenseAttachments = attachments.stream()
                .map(attachment -> tutorSupportMapper.toDefenseAttachment(
                        attachment,
                        storageService.generateSignedUrl(
                                attachment.getStoragePath(), StorageConstants.SIGNED_URL_EXPIRATION_SECONDS)))
                .toList();
        String previewUrl = storageService.generateSignedUrl(
                material.getStoragePath(), StorageConstants.SIGNED_URL_EXPIRATION_SECONDS);
        return tutorSupportMapper.toReportedMaterialDetails(
                material, reports, defense, previewUrl, defenseAttachments);
    }

    private AcademicMaterial findReportedMaterialOrThrow(UUID tutorUserId, UUID materialId) {
        return materialRepository.findActiveByIdAndTutorUserId(materialId, tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REPORTED_MATERIAL_NOT_FOUND",
                        "El material denunciado no existe.",
                        "Reported material " + materialId + " not found for tutor " + tutorUserId));
    }

    private List<AcademicMaterialReport> findReportsOrThrow(UUID materialId) {
        List<AcademicMaterialReport> reports = materialReportRepository.findDetailsByMaterialId(materialId);
        if (reports.isEmpty()) {
            throw new ResourceNotFoundException(
                    "REPORTED_MATERIAL_NOT_FOUND",
                    "El material denunciado no existe.",
                    "No reports found for material " + materialId);
        }
        return reports;
    }

    private void cleanupUploads(List<String> paths) {
        for (String path : paths) {
            try {
                storageService.delete(path);
            } catch (Exception exception) {
                log.warn("Failed to clean up material defense attachment {}: {}", path, exception.getMessage());
            }
        }
    }
}
