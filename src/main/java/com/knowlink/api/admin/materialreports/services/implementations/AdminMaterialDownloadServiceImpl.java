package com.knowlink.api.admin.materialreports.services.implementations;

import com.knowlink.api.admin.common.audit.AdminAuditAction;
import com.knowlink.api.admin.common.audit.IAdminAuditService;
import com.knowlink.api.admin.materialreports.controllers.responses.DownloadUrlResponse;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportCaseRepository;
import com.knowlink.api.admin.materialreports.services.interfaces.IAdminMaterialDownloadService;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMaterialDownloadServiceImpl implements IAdminMaterialDownloadService {

    private static final String TARGET_TYPE = "MATERIAL_REPORT_CASE";
    private static final int EXPIRATION_SECONDS = 3600;

    private final IMaterialReportCaseRepository caseRepository;
    private final ISupabaseStorageService storageService;
    private final IAdminAuditService auditService;

    @Override
    @Transactional
    public DownloadUrlResponse generateDownloadUrl(UUID caseId, UUID adminUserId) {
        MaterialReportCase reportCase = caseRepository.findDetailById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_REPORT_CASE_NOT_FOUND",
                        "La denuncia no existe.",
                        "Material report case not found: " + caseId));

        String url;
        try {
            url = storageService.generateSignedUrl(
                    reportCase.getAcademicMaterial().getStoragePath(), EXPIRATION_SECONDS);
        } catch (RuntimeException exception) {
            log.warn("Could not generate signed URL for material report case {} ({}); the storage error message is not logged to avoid leaking signed URLs",
                    caseId, exception.getClass().getSimpleName());
            throw new ResourceNotFoundException(
                    "FILE_NOT_FOUND",
                    "El archivo no está disponible.",
                    "Stored file not found for material report case: " + caseId);
        }

        auditService.record(adminUserId, AdminAuditAction.MATERIAL_DOWNLOADED, TARGET_TYPE, caseId);
        log.info("Admin {} requested download for material report case {}", adminUserId, caseId);

        return new DownloadUrlResponse(url, EXPIRATION_SECONDS);
    }
}