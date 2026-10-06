package com.knowlink.api.materials.service.implementations;

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
        when(materialAccessService.listarMaterialesAccesibles(studentId, tutorUserId, subjectId))
                .thenReturn(List.of(new MaterialResponse(
                        materialId,
                        material.getName(),
                        material.getOriginalFileName(),
                        subjectId,
                        subject.getName(),
                        tutorUserId,
                        tutorUser.getFullName(),
                        material.getMaterialType().name(),
                        null,
                        material.getUploadedAt(),
                        material.getSizeInBytes(),
                        0,
                        false
                )));

        List<MaterialResponse> result = service.listBySubject(subjectId, studentId, Role.STUDENT.name());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(materialId);
        verify(materialAccessService).listarMaterialesAccesibles(studentId, tutorUserId, subjectId);
    }
}
