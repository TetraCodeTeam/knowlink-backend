package com.knowlink.api.materials.controllers;

import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportResponse;
import com.knowlink.api.materials.exception.ResourceAccessDeniedException;
import com.knowlink.api.materials.service.interfaces.IMaterialAccessService;
import com.knowlink.api.materials.service.interfaces.IMaterialReportService;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private IAcademicMaterialReportRepository materialReportRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private IMaterialAccessService materialAccessService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private IMaterialReportService materialReportService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private ISupabaseStorageService supabaseStorageService;

    private User tutorUser;
    private User studentUser;
        private User adminUser;
    private Career career;
    private Subject subject;
    private AcademicMaterial existingMaterial;

    @BeforeEach
    void setUp() {
        when(supabaseStorageService.upload(any(), any(UUID.class)))
                .thenReturn("test-bucket/test-uuid.pdf");
        when(supabaseStorageService.generateSignedUrl(any(), any(int.class)))
                .thenReturn("https://test.supabase.co/signed-url");

        career = careerRepository.save(Career.builder()
                .name("Ingenieria en Sistemas " + UUID.randomUUID())
                .build());

        subject = subjectRepository.save(Subject.builder()
                .name("Analisis Matematico II " + UUID.randomUUID())
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

        adminUser = userRepository.save(User.builder()
                .fullName("Admin Test")
                .email("admin-" + UUID.randomUUID() + "@test.com")
                .password("password123")
                .role(Role.ADMIN)
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

    @Test
    @DisplayName("AC2: student without any reservation gets 403 on listBySubject")
    void studentWithoutReservation_gets403OnList() throws Exception {
        when(materialAccessService.listAccessibleMaterials(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/materials")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(studentAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El alumno no tiene reserva activa con este tutor"));
    }

    @Test
    @DisplayName("AC2: student without reservation gets 403 on download")
    void studentWithoutReservation_gets403OnDownload() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceAccessDeniedException("No tenés acceso a este material"))
                .when(materialAccessService)
                .validateAccessOrDeny(any(UUID.class), eq(existingMaterial.getAcademicMaterialId()));

        mockMvc.perform(get("/api/v1/materials/" + existingMaterial.getAcademicMaterialId() + "/download")
                        .with(studentAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tenés acceso a este material"));
    }

    @Test
    @DisplayName("US-27: student can report an academic material")
    void studentReportsMaterial_returns201AndConfirmation() throws Exception {
        when(materialReportService.reportMaterial(
                eq(studentUser.getUserId()),
                eq(existingMaterial.getAcademicMaterialId()),
                any(MaterialReportRequest.class)))
                .thenReturn(new MaterialReportResponse("Tu reporte fue enviado correctamente."));

        mockMvc.perform(post("/api/v1/materials/{id}/reports", existingMaterial.getAcademicMaterialId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"reason":"PLAGIARISM","description":"Copiado sin referencia"}
                                """)
                        .with(studentAuth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tu reporte fue enviado correctamente."));

        verify(materialReportService).reportMaterial(
                eq(studentUser.getUserId()),
                eq(existingMaterial.getAcademicMaterialId()),
                any(MaterialReportRequest.class));
    }

    @Test
    @DisplayName("US-27: tutor cannot report materials through the student endpoint")
    void tutorReportsMaterial_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/materials/{id}/reports", existingMaterial.getAcademicMaterialId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"reason":"OTHER"}
                                """)
                        .with(tutorAuth()))
                .andExpect(status().isForbidden());
    }

        @Test
        @DisplayName("US-27: admin can see material report count")
        void adminListsMaterials_seesReportsCount() throws Exception {
                existingMaterial.setReportsCount(3);
                materialRepository.save(existingMaterial);

                mockMvc.perform(get("/api/v1/materials")
                                                .param("subjectId", subject.getSubjectId().toString())
                                                .with(adminAuth()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].reportsCount").value(3));
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


    @Test
    @DisplayName("US-17: tutor deletes own material returns 204 and removes the stored file")
    void tutorDeletesOwnMaterial_returns204() throws Exception {
        UUID materialId = existingMaterial.getAcademicMaterialId();
        String storagePath = existingMaterial.getStoragePath();

        mockMvc.perform(delete("/api/v1/materials/{id}", materialId).with(tutorAuth()))
                .andExpect(status().isNoContent());

        assertThat(materialRepository.findById(materialId)).isEmpty();
        verify(supabaseStorageService).delete(storagePath);
    }

    @Test
    @DisplayName("US-17: tutor cannot delete material owned by another tutor")
    void tutorCannotDeleteAnotherTutorsMaterial_returns403() throws Exception {
        User otherTutor = userRepository.save(User.builder()
                .fullName("Other Tutor")
                .email("other-tutor-" + UUID.randomUUID() + "@test.com")
                .password("password123")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        tutorProfileRepository.save(TutorProfile.builder()
                .user(otherTutor)
                .career(career)
                .build());

        UserPrincipal principal = new UserPrincipal(otherTutor);
        RequestPostProcessor otherTutorAuth = SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        mockMvc.perform(delete("/api/v1/materials/{id}", existingMaterial.getAcademicMaterialId())
                        .with(otherTutorAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ACCESS_DENIED"));

        assertThat(materialRepository.findById(existingMaterial.getAcademicMaterialId())).isPresent();
        verify(supabaseStorageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("US-17: student cannot delete material")
    void studentCannotDeleteMaterial_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/materials/{id}", existingMaterial.getAcademicMaterialId())
                        .with(studentAuth()))
                .andExpect(status().isForbidden());

        assertThat(materialRepository.findById(existingMaterial.getAcademicMaterialId())).isPresent();
        verify(supabaseStorageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("US-17: deleting a missing material returns 404")
    void deleteMissingMaterial_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/materials/{id}", UUID.randomUUID()).with(tutorAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    @DisplayName("US-17: deleting a reported material returns 409 and keeps it")
    void deleteMaterialWithReports_returns409() throws Exception {
        UUID materialId = existingMaterial.getAcademicMaterialId();

        materialReportRepository.save(AcademicMaterialReport.builder()
                .material(existingMaterial)
                .reporter(studentUser)
                .reason(MaterialReportReason.PLAGIARISM)
                .build());

        mockMvc.perform(delete("/api/v1/materials/{id}", materialId).with(tutorAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("MATERIAL_HAS_REPORTS"));

        assertThat(materialRepository.findById(materialId)).isPresent();
        verify(supabaseStorageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("US-17: deleted material is no longer downloadable nor listed")
    void deletedMaterial_disappearsFromDownloadAndLists() throws Exception {
        UUID materialId = existingMaterial.getAcademicMaterialId();

        mockMvc.perform(delete("/api/v1/materials/{id}", materialId).with(tutorAuth()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/materials/{id}/download", materialId).with(tutorAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("MATERIAL_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/materials")
                        .param("subjectId", subject.getSubjectId().toString())
                        .with(tutorAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("US-17: unauthenticated delete returns 401")
    void unauthenticatedDelete_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/materials/{id}", existingMaterial.getAcademicMaterialId()))
                .andExpect(status().isUnauthorized());
    }

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

        private RequestPostProcessor adminAuth() {
                UserPrincipal principal = new UserPrincipal(adminUser);
                return SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        }
}
