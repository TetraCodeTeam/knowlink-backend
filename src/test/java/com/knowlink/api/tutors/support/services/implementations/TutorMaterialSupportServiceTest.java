package com.knowlink.api.tutors.support.services.implementations;

import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.policy.MaterialReportingPolicyProvider;
import com.knowlink.api.materials.repositories.IAcademicMaterialDefenseRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.MaterialDefenseAttachmentRepository;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.materials.utils.StorageConstants;
import com.knowlink.api.tutors.support.data.mappers.TutorSupportMapper;
import com.knowlink.api.tutors.support.validations.MaterialDefenseValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorMaterialSupportServiceTest {

    @Mock
    private IAcademicMaterialReportRepository materialReportRepository;

    @Mock
    private IAcademicMaterialRepository materialRepository;

    @Mock
    private IAcademicMaterialDefenseRepository materialDefenseRepository;

    @Mock
    private MaterialDefenseAttachmentRepository defenseAttachmentRepository;

    @Mock
    private ISupabaseStorageService storageService;

    @Mock
    private TutorSupportMapper tutorSupportMapper;

    @Mock
    private MaterialDefenseValidator materialDefenseValidator;

    @Mock
    private MaterialReportingPolicyProvider materialReportingPolicyProvider;

    @InjectMocks
    private TutorMaterialSupportService service;

    @Test
    @DisplayName("Material denunciado - la vista previa se firma con el bucket de materiales configurado")
    void getReportedMaterialDetails_signsPreviewWithConfiguredMaterialsBucket() {
        UUID tutorUserId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        AcademicMaterial material = AcademicMaterial.builder()
                .academicMaterialId(materialId)
                .storagePath("subject/material.pdf")
                .build();
        when(materialRepository.findActiveByIdAndTutorUserId(materialId, tutorUserId))
                .thenReturn(Optional.of(material));
        when(materialReportRepository.findDetailsByMaterialId(materialId))
                .thenReturn(List.of(AcademicMaterialReport.builder().build()));
        when(materialDefenseRepository.findFirstByMaterial_AcademicMaterialIdOrderBySubmittedAtDesc(materialId))
                .thenReturn(Optional.empty());

        service.getReportedMaterialDetails(tutorUserId, materialId);

        // La sobrecarga sin bucket delega en `supabase.storage.materials-bucket`; con bucket explícito
        // se podría volver a fijar un nombre que no existe en Supabase.
        verify(storageService).generateSignedUrl(
                "subject/material.pdf", StorageConstants.SIGNED_URL_EXPIRATION_SECONDS);
        verify(storageService, never()).generateSignedUrl(anyString(), anyString(), anyInt());
    }
}
