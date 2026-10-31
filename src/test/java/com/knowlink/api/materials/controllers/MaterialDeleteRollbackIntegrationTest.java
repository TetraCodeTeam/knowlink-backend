package com.knowlink.api.materials.controllers;

import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.exception.StorageServiceException;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.service.interfaces.IMaterialAccessService;
import com.knowlink.api.materials.service.interfaces.IMaterialReportService;
import com.knowlink.api.materials.service.interfaces.ISupabaseStorageService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-17: a Storage failure must roll back the row deletion. This class deliberately does
 * not use @Transactional so the service transaction is the only one in play and the
 * rollback is real. Fixtures are removed manually in @AfterEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaterialDeleteRollbackIntegrationTest {

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

    @MockitoBean
    private IMaterialAccessService materialAccessService;

    @MockitoBean
    private IMaterialReportService materialReportService;

    @MockitoBean
    private ISupabaseStorageService supabaseStorageService;

    private User tutorUser;
    private Career career;
    private Subject subject;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;
    private AcademicMaterial material;

    @AfterEach
    void cleanUp() {
        if (material != null && material.getAcademicMaterialId() != null) {
            materialRepository.deleteById(material.getAcademicMaterialId());
        }
        if (tutorSubject != null && tutorSubject.getTutorSubjectId() != null) {
            tutorSubjectRepository.deleteById(tutorSubject.getTutorSubjectId());
        }
        if (tutorProfile != null && tutorProfile.getTutorProfileId() != null) {
            tutorProfileRepository.deleteById(tutorProfile.getTutorProfileId());
        }
        if (subject != null && subject.getSubjectId() != null) {
            subjectRepository.deleteById(subject.getSubjectId());
        }
        if (career != null && career.getCareerId() != null) {
            careerRepository.deleteById(career.getCareerId());
        }
        if (tutorUser != null && tutorUser.getUserId() != null) {
            userRepository.deleteById(tutorUser.getUserId());
        }
    }

    @Test
    @DisplayName("US-17: storage failure returns 502 STORAGE_ERROR and rolls back the row deletion")
    void storageFailure_returns502AndKeepsMaterial() throws Exception {
        createFixture();
        String storagePath = material.getStoragePath();
        UUID materialId = material.getAcademicMaterialId();

        doThrow(new StorageServiceException("storage unavailable", new RuntimeException("boom")))
                .when(supabaseStorageService).delete(storagePath);

        mockMvc.perform(delete("/api/v1/materials/{id}", materialId).with(tutorAuth()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("STORAGE_ERROR"));

        assertThat(materialRepository.findById(materialId)).isPresent();
    }

    private void createFixture() {
        career = careerRepository.save(Career.builder()
                .name("Ingenieria en Sistemas " + UUID.randomUUID())
                .build());

        subject = subjectRepository.save(Subject.builder()
                .name("Analisis Matematico II " + UUID.randomUUID())
                .isBasic(false)
                .career(career)
                .build());

        tutorUser = userRepository.save(User.builder()
                .fullName("Tutor Rollback")
                .email("tutor-rollback-" + UUID.randomUUID() + "@test.com")
                .password("password123")
                .role(Role.TUTOR)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorUser)
                .career(career)
                .build());

        tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());

        material = materialRepository.save(AcademicMaterial.builder()
                .tutorSubject(tutorSubject)
                .name("Material Rollback")
                .originalFileName("rollback.pdf")
                .storagePath(subject.getSubjectId() + "/rollback-uuid.pdf")
                .materialType(MaterialType.PDF)
                .sizeInBytes(1024L)
                .reportsCount(0)
                .available(true)
                .active(true)
                .build());
    }

    private RequestPostProcessor tutorAuth() {
        UserPrincipal principal = new UserPrincipal(tutorUser);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
