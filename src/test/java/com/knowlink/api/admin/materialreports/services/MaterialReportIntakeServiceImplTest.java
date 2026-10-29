package com.knowlink.api.admin.materialreports.services;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportCaseRepository;
import com.knowlink.api.admin.materialreports.services.implementations.MaterialReportIntakeServiceImpl;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialReportIntakeServiceImplTest {

    @Mock
    private IAcademicMaterialRepository materialRepository;
    @Mock
    private IAcademicMaterialReportRepository reportRepository;
    @Mock
    private IMaterialReportCaseRepository caseRepository;
    @Mock
    private IUserRepository userRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    private MaterialReportIntakeServiceImpl service;
    private UUID materialId;
    private UUID reporterId;
    private AcademicMaterial material;
    private User reporter;

    @BeforeEach
    void setUp() {
        service = new MaterialReportIntakeServiceImpl(
                materialRepository, reportRepository, caseRepository, userRepository, clock);
        materialId = UUID.randomUUID();
        reporterId = UUID.randomUUID();
        material = AcademicMaterial.builder()
                .academicMaterialId(materialId)
                .name("Material")
                .active(true)
                .available(true)
                .build();
        reporter = User.builder().userId(reporterId).build();
    }

    @Test
    void addsReportToExistingOpenCaseAndIncrementsCounter() {
        MaterialReportCase openCase = MaterialReportCase.builder()
                .materialReportCaseId(UUID.randomUUID())
                .academicMaterial(material)
                .status(MaterialReportCaseStatus.IN_REVIEW)
                .openedAt(LocalDateTime.of(2026, 10, 20, 9, 0))
                .reportsCount(2)
                .build();

        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(Optional.of(material));
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(caseRepository.findOpenCasesByMaterialId(materialId)).thenReturn(List.of(openCase));
        when(reportRepository.saveAndFlush(any(AcademicMaterialReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AcademicMaterialReport result = service.registerReport(
                materialId, reporterId, MaterialReportReason.PLAGIARISM, "copied text");

        assertThat(result.getReportCase()).isSameAs(openCase);
        assertThat(result.getMaterial()).isSameAs(material);
        assertThat(result.getReporter()).isSameAs(reporter);
        assertThat(openCase.getReportsCount()).isEqualTo(3);
        verify(caseRepository).save(openCase);
    }

    @Test
    void opensNewPendingCaseWhenMaterialHasNoOpenCase() {
        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(Optional.of(material));
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(caseRepository.findOpenCasesByMaterialId(materialId)).thenReturn(List.of());
        when(reportRepository.saveAndFlush(any(AcademicMaterialReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(caseRepository.save(any(MaterialReportCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AcademicMaterialReport result = service.registerReport(
                materialId, reporterId, MaterialReportReason.INAPPROPRIATE_CONTENT, null);

        ArgumentCaptor<MaterialReportCase> captor = ArgumentCaptor.forClass(MaterialReportCase.class);
        verify(caseRepository).save(captor.capture());
        MaterialReportCase savedCase = captor.getValue();

        assertThat(result.getReportCase()).isSameAs(savedCase);
        assertThat(savedCase.getStatus()).isEqualTo(MaterialReportCaseStatus.PENDING);
        assertThat(savedCase.getReportsCount()).isEqualTo(1);
        assertThat(savedCase.getOpenedAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
    }

    @Test
    void rejectsInactiveMaterial() {
        material.setActive(false);
        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(Optional.of(material));

        assertThatThrownBy(() -> service.registerReport(
                materialId, reporterId, MaterialReportReason.PLAGIARISM, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(exception -> assertThat(((ResourceNotFoundException) exception).getErrorCode())
                        .isEqualTo("MATERIAL_NOT_FOUND"));

        verify(reportRepository, never()).saveAndFlush(any());
        verifyNoInteractions(caseRepository, userRepository);
    }
}