package com.knowlink.api.materials.service.implementations;

import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.exception.ResourceAccessDeniedException;
import com.knowlink.api.materials.exception.StorageServiceException;
import com.knowlink.api.materials.controller.responses.MaterialResponse;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.service.interfaces.IMaterialAccessService;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {

    @Mock
    private IAcademicMaterialRepository materialRepository;
    @Mock
    private IAcademicMaterialReportRepository reportRepository;
    @Mock
    private ISubjectRepository subjectRepository;
    @Mock
    private ITutorProfileRepository tutorProfileRepository;
    @Mock
    private ITutorSubjectRepository tutorSubjectRepository;
    @Mock
    private ISupabaseStorageService supabaseStorageService;
    @Mock
    private IMaterialAccessService materialAccessService;

    private MaterialServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MaterialServiceImpl(
                materialRepository,
                reportRepository,
                subjectRepository,
                tutorProfileRepository,
                tutorSubjectRepository,
                supabaseStorageService,
                materialAccessService
        );
    }

    @Test
    void listBySubject_studentUsesMaterialAccessServiceForRealAccessValidation() {
        UUID subjectId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID tutorUserId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        Subject subject = Subject.builder()
                .subjectId(subjectId)
                .name("Matemática")
                .build();

        User tutorUser = User.builder()
                .userId(tutorUserId)
                .fullName("Ana Tutor")
                .build();

        TutorProfile tutorProfile = TutorProfile.builder()
                .user(tutorUser)
                .build();

        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .build();

        AcademicMaterial material = AcademicMaterial.builder()
                .academicMaterialId(materialId)
                .name("Apunte de cálculo")
                .originalFileName("apunte.pdf")
                .storagePath("materials/apunte.pdf")
                .uploadedAt(LocalDateTime.now())
                .sizeInBytes(120L)
                .available(true)
                .active(true)
                .materialType(MaterialType.PDF)
                .tutorSubject(tutorSubject)
                .build();

        when(materialRepository.findActiveBySubjectId(subjectId)).thenReturn(List.of(material));
        when(reportRepository.findReportedMaterialIds(studentId, List.of(materialId))).thenReturn(Set.of());
        when(materialAccessService.hasAccess(studentId, tutorUserId, subjectId)).thenReturn(true);

        List<MaterialResponse> result = service.listBySubject(subjectId, studentId, Role.STUDENT.name());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(materialId);
        verify(materialAccessService).hasAccess(studentId, tutorUserId, subjectId);
    }

    @Test
    void delete_ownerTutor_removesRowAndStoredFileInOrder() {
        UUID tutorUserId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        AcademicMaterial material = materialOwnedBy(tutorUserId, materialId);

        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(java.util.Optional.of(material));
        when(reportRepository.existsByMaterialAcademicMaterialId(materialId)).thenReturn(false);

        service.delete(materialId, tutorUserId);

        org.mockito.InOrder inOrder = inOrder(materialRepository, supabaseStorageService);
        inOrder.verify(materialRepository).delete(material);
        inOrder.verify(materialRepository).flush();
        inOrder.verify(supabaseStorageService).delete("materials/material.pdf");
    }

    @Test
    void delete_materialOfAnotherTutor_isDenied() {
        UUID tutorUserId = UUID.randomUUID();
        UUID otherTutorUserId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        AcademicMaterial material = materialOwnedBy(tutorUserId, materialId);

        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(java.util.Optional.of(material));

        assertThatThrownBy(() -> service.delete(materialId, otherTutorUserId))
                .isInstanceOf(ResourceAccessDeniedException.class)
                .hasMessage("No tenés permiso para eliminar este material");

        verify(materialRepository, never()).delete(any());
        verify(materialRepository, never()).flush();
        verify(supabaseStorageService, never()).delete(any(String.class));
    }

    @Test
    void delete_missingMaterial_throwsNotFound() {
        UUID materialId = UUID.randomUUID();
        UUID tutorUserId = UUID.randomUUID();

        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(materialId, tutorUserId))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MATERIAL_NOT_FOUND"));

        verify(materialRepository, never()).delete(any());
        verify(supabaseStorageService, never()).delete(any(String.class));
    }

    @Test
    void delete_materialWithReports_isBlocked() {
        UUID tutorUserId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        AcademicMaterial material = materialOwnedBy(tutorUserId, materialId);

        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(java.util.Optional.of(material));
        when(reportRepository.existsByMaterialAcademicMaterialId(materialId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(materialId, tutorUserId))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MATERIAL_HAS_REPORTS"));

        verify(materialRepository, never()).delete(any());
        verify(materialRepository, never()).flush();
        verify(supabaseStorageService, never()).delete(any(String.class));
    }

    @Test
    void delete_unavailableOrInactiveMaterialWithoutReports_isAllowed() {
        UUID tutorUserId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        AcademicMaterial material = materialOwnedBy(tutorUserId, materialId);
        material.setActive(false);
        material.setAvailable(false);

        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(java.util.Optional.of(material));
        when(reportRepository.existsByMaterialAcademicMaterialId(materialId)).thenReturn(false);

        service.delete(materialId, tutorUserId);

        verify(materialRepository).delete(material);
        verify(materialRepository).flush();
        verify(supabaseStorageService).delete("materials/material.pdf");
    }

    @Test
    void delete_whenStorageFails_propagatesException() {
        UUID tutorUserId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        AcademicMaterial material = materialOwnedBy(tutorUserId, materialId);

        when(materialRepository.findByIdForUpdate(materialId)).thenReturn(java.util.Optional.of(material));
        when(reportRepository.existsByMaterialAcademicMaterialId(materialId)).thenReturn(false);
        doThrow(new StorageServiceException("storage down", new RuntimeException("boom")))
                .when(supabaseStorageService).delete("materials/material.pdf");

        assertThatThrownBy(() -> service.delete(materialId, tutorUserId))
                .isInstanceOf(StorageServiceException.class);

        verify(materialRepository).delete(material);
        verify(materialRepository).flush();
    }

    private AcademicMaterial materialOwnedBy(UUID tutorUserId, UUID materialId) {
        User owner = User.builder()
                .userId(tutorUserId)
                .fullName("Tutor Owner")
                .build();

        TutorProfile tutorProfile = TutorProfile.builder()
                .user(owner)
                .build();

        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .build();

        return AcademicMaterial.builder()
                .academicMaterialId(materialId)
                .name("Material")
                .originalFileName("material.pdf")
                .storagePath("materials/material.pdf")
                .materialType(MaterialType.PDF)
                .sizeInBytes(100L)
                .reportsCount(0)
                .available(true)
                .active(true)
                .tutorSubject(tutorSubject)
                .build();
    }
}

