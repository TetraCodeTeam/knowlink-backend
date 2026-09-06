package com.knowlink.api.resources.controllers;

import com.knowlink.api.resources.service.interfaces.IReservationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.MaterialType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.AcademicMaterial;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.IAcademicMaterialRepository;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MaterialControllerImplTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ICareerRepository careerRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ITutorProfileRepository tutorProfileRepository;

    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;

    @Autowired
    private IAcademicMaterialRepository materialRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private IReservationService reservationService;

    private User tutorUser;
    private User studentUser;
    private Subject subject;
    private AcademicMaterial existingMaterial;

    @BeforeEach
    void setUp() {
        Career career = careerRepository.save(Career.builder()
                .name("Ingeniería en Sistemas " + UUID.randomUUID())
                .build());

        subject = subjectRepository.save(Subject.builder()
                .name("Análisis Matemático II " + UUID.randomUUID())
                .isBasic(false)
                .career(career)
                .build());

        tutorUser = userRepository.save(User.builder()
                .fullName("Tutor Test")
                .email("tutor-" + UUID.randomUUID() + "@test.com")
                .password("password123")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        studentUser = userRepository.save(User.builder()
                .fullName("Student Test")
                .email("student-" + UUID.randomUUID() + "@test.com")
                .password("password123")
                .role(Role.STUDENT)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        TutorProfile tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorUser)
                .career(career)
                .build());

        TutorSubject tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());

        existingMaterial = materialRepository.save(AcademicMaterial.builder()
                .tutorSubject(tutorSubject)
                .name("Material Existente")
                .originalFileName("existente.pdf")
                .storagePath(subject.getSubjectId() + "/test-uuid-existente.pdf")
                .materialType(MaterialType.PDF)
                .sizeInBytes(1024L)
                .reportsCount(0)
                .available(true)
                .active(true)
                .build());
    }

    // ==================== AC1 — Carga con nombre y materia ====================

    @Test
    @DisplayName("AC1: tutor uploads material with valid name and subject returns 201")
    void tutorUploadsMaterial_returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resumen.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("name", "Resumen Unidad 3 - Derivadas")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(tutorAuth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Resumen Unidad 3 - Derivadas"));
    }

    @Test
    @DisplayName("AC1: upload without name returns 400")
    void uploadWithoutName_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resumen.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(tutorAuth()))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC3 — Materia obligatoria ====================

    @Test
    @DisplayName("AC3: upload without subjectId returns 400")
    void uploadWithoutSubjectId_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resumen.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("name", "Resumen")
                        .with(tutorAuth()))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC4 — Formatos no permitidos ====================

    @Test
    @DisplayName("AC4: upload with .docx returns 400")
    void uploadWithDocx_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resumen.docx", "application/msword", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("name", "Resumen")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(tutorAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC4: upload with .jpg returns 400")
    void uploadWithJpg_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", "image/jpeg", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("name", "Foto")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(tutorAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC4: upload with .zip returns 400")
    void uploadWithZip_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.zip", "application/zip", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("name", "Archivo")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(tutorAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC4: upload with .mp4 returns 400")
    void uploadWithMp4_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "video.mp4", "video/mp4", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("name", "Video")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(tutorAuth()))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC2 — Seguridad: reserva por tutor + materia ====================

    @Test
    @DisplayName("AC2: student without any reservation gets 403 on listBySubject")
    void studentWithoutReservation_gets403OnList() throws Exception {
        when(reservationService.hasAnyReservationForSubject(any(UUID.class), any(UUID.class)))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/materials")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(studentAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El alumno no tiene reserva activa con este tutor"));
    }

    @Test
    @DisplayName("AC2: student without reservation gets 403 on download")
    void studentWithoutReservation_gets403OnDownload() throws Exception {
        when(reservationService.hasReservationWithTutorForSubject(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/materials/" + existingMaterial.getAcademicMaterialId() + "/download")
                        .with(studentAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El alumno no tiene reserva activa con este tutor"));
    }

    @Test
    @DisplayName("AC2: unauthenticated user gets 401")
    void unauthenticatedUser_gets401() throws Exception {
        mockMvc.perform(get("/api/v1/materials")
                        .param("subjectId", subject.getSubjectId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC2: student cannot upload material (only TUTOR role allowed)")
    void studentTriesToUpload_gets403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resumen.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/materials")
                        .file(file)
                        .param("name", "Resumen")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(studentAuth()))
                .andExpect(status().isForbidden());
    }

    // ==================== Helpers ====================

    private RequestPostProcessor tutorAuth() {
        UserPrincipal principal = new UserPrincipal(tutorUser);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private RequestPostProcessor studentAuth() {
        UserPrincipal principal = new UserPrincipal(studentUser);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
