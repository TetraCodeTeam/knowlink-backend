package com.knowlink.api.materials.services;

import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.materials.controller.responses.MaterialResponse;
import com.knowlink.api.materials.service.implementations.MaterialAccessServiceImpl;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialAccessServiceImplTest {

    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private IAcademicMaterialRepository materialRepository;
    @Mock
    private IAcademicMaterialReportRepository reportRepository;

    private MaterialAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MaterialAccessServiceImpl(bookingRepository, materialRepository, reportRepository);
    }

    @Test
    void accessibleMaterialResponseIncludesAlreadyReportedAndCount() {
        UUID studentId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        User tutor = User.builder().userId(tutorId).fullName("Tutor").build();
        TutorProfile tutorProfile = TutorProfile.builder().user(tutor).build();
        Subject subject = Subject.builder().subjectId(subjectId).name("Matemática").build();
        TutorSubject tutorSubject = TutorSubject.builder().tutorProfile(tutorProfile).subject(subject).build();
        AcademicMaterial material = AcademicMaterial.builder()
                .academicMaterialId(materialId)
                .tutorSubject(tutorSubject)
                .name("Guía")
                .materialType(MaterialType.PDF)
                .reportsCount(2)
                .build();
        when(bookingRepository.findCompletedSubjectIdsByStudentAndTutor(studentId, tutorId))
                .thenReturn(List.of(subjectId));
        when(materialRepository.findAccessibleByTutorAndSubjectIds(tutorId, List.of(subjectId)))
                .thenReturn(List.of(material));
        when(reportRepository.findReportedMaterialIds(studentId, List.of(materialId)))
                .thenReturn(Set.of(materialId));

        List<MaterialResponse> responses = service.listAccessibleMaterials(studentId, tutorId, subjectId);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.alreadyReported()).isTrue();
            assertThat(response.reportsCount()).isZero();
        });
    }
}