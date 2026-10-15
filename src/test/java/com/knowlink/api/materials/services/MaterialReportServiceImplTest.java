package com.knowlink.api.materials.services;

import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.materials.exception.ResourceAccessDeniedException;
import com.knowlink.api.materials.service.implementations.MaterialReportServiceImpl;
import com.knowlink.api.materials.service.interfaces.IMaterialAccessService;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.mappers.AcademicMaterialReportMapper;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialReportServiceImplTest {

    @Mock
    private IAcademicMaterialRepository materialRepository;
    @Mock
    private IAcademicMaterialReportRepository reportRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private IMaterialAccessService materialAccessService;

    private MaterialReportServiceImpl service;
    private UUID materialId;
    private UUID studentId;
    private User tutor;
    private User student;
    private AcademicMaterial material;

    @BeforeEach
    void setUp() {
        service = new MaterialReportServiceImpl(
                materialRepository,
                reportRepository,
                userRepository,
                materialAccessService,
                new AcademicMaterialReportMapper());
        materialId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        tutor = User.builder().userId(UUID.randomUUID()).build();
        student = User.builder().userId(studentId).build();
        TutorProfile tutorProfile = TutorProfile.builder().user(tutor).build();
        TutorSubject tutorSubject = TutorSubject.builder().tutorProfile(tutorProfile).build();
        material = AcademicMaterial.builder()
                .academicMaterialId(materialId)
                .tutorSubject(tutorSubject)
                .reportsCount(3)
                .active(true)
                .build();
        when(materialRepository.findByAcademicMaterialIdAndActiveTrueAndAvailableTrue(materialId))
                .thenReturn(Optional.of(material));
    }

    @Test
    void reportWithCompletedAccessPersistsReportAndAtomicallyIncrementsCount() {
        MaterialReportRequest request = new MaterialReportRequest(MaterialReportReason.PLAGIARISM, "  Copiado  ");
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(materialRepository.incrementReportsCount(materialId)).thenReturn(1);

        var response = service.reportMaterial(studentId, materialId, request);

        ArgumentCaptor<AcademicMaterialReport> reportCaptor = ArgumentCaptor.forClass(AcademicMaterialReport.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        verify(materialAccessService).validateAccessOrDeny(studentId, materialId);
        verify(materialRepository).incrementReportsCount(materialId);
        assertThat(reportCaptor.getValue().getReason()).isEqualTo(MaterialReportReason.PLAGIARISM);
        assertThat(reportCaptor.getValue().getDescription()).isEqualTo("Copiado");
        assertThat(response.message()).isEqualTo("Tu reporte fue enviado correctamente.");
    }

    @Test
    void studentWithoutCompletedAccessCannotReportMaterial() {
        doThrow(new ResourceAccessDeniedException("No tenés acceso a este material"))
                .when(materialAccessService).validateAccessOrDeny(studentId, materialId);

        assertThatThrownBy(() -> service.reportMaterial(studentId, materialId,
                new MaterialReportRequest(MaterialReportReason.OTHER, null)))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verifyNoInteractions(reportRepository);
    }

    @Test
    void tutorCannotReportOwnMaterial() {
        UUID tutorId = tutor.getUserId();

        assertThatThrownBy(() -> service.reportMaterial(tutorId, materialId,
                new MaterialReportRequest(MaterialReportReason.OTHER, null)))
                .isInstanceOf(ResourceAccessDeniedException.class)
                .hasMessage("No podés denunciar tu propio material.");

        verifyNoInteractions(materialAccessService, reportRepository);
    }

    @Test
    void duplicateReportReturnsStableConflict() {
        when(reportRepository.existsByMaterialAcademicMaterialIdAndReporterUserId(materialId, studentId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reportMaterial(studentId, materialId,
                new MaterialReportRequest(MaterialReportReason.FALSE_INFORMATION, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .satisfies(exception -> {
                    DuplicateResourceException duplicate = (DuplicateResourceException) exception;
                    assertThat(duplicate.getErrorCode()).isEqualTo("MATERIAL_ALREADY_REPORTED");
                    assertThat(duplicate.getUserMessage()).isEqualTo("Ya reportaste este material anteriormente");
                });

        verify(reportRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(materialRepository, never()).incrementReportsCount(materialId);
    }

    @Test
    void concurrentDuplicateIsTranslatedToSameConflict() {
        when(reportRepository.existsByMaterialAcademicMaterialIdAndReporterUserId(materialId, studentId))
                .thenReturn(false);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(reportRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(AcademicMaterialReport.class)))
                .thenThrow(new DataIntegrityViolationException("unique key"));

        assertThatThrownBy(() -> service.reportMaterial(studentId, materialId,
                new MaterialReportRequest(MaterialReportReason.OTHER, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .satisfies(exception -> assertThat(((DuplicateResourceException) exception).getErrorCode())
                        .isEqualTo("MATERIAL_ALREADY_REPORTED"));

        verify(materialRepository, never()).incrementReportsCount(materialId);
    }
}