package com.knowlink.api.materials.repositories;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class AcademicMaterialReportRepositoryTest {

    @Autowired
    private IAcademicMaterialRepository materialRepository;
    @Autowired
    private IAcademicMaterialReportRepository reportRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private User reporter;
    private AcademicMaterial firstMaterial;
    private AcademicMaterial secondMaterial;

    @BeforeEach
    void setUp() {
        Career career = persist(Career.builder().name("Career " + UUID.randomUUID()).build());
        User tutor = persist(user("tutor", Role.TUTOR));
        reporter = persist(user("student", Role.STUDENT));
        TutorProfile tutorProfile = persist(TutorProfile.builder().user(tutor).career(career).build());
        Subject subject = persist(Subject.builder()
                .name("Subject " + UUID.randomUUID())
                .career(career)
                .isBasic(true)
                .build());
        TutorSubject tutorSubject = persist(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
        firstMaterial = persist(material(tutorSubject, "first.pdf"));
        secondMaterial = persist(material(tutorSubject, "second.pdf"));
    }

    @Test
    void reportedMaterialIdsAreReturnedInOneBatch() {
        persist(report(firstMaterial, reporter));
        entityManager.flush();

        Set<UUID> reportedIds = reportRepository.findReportedMaterialIds(
                reporter.getUserId(), List.of(firstMaterial.getAcademicMaterialId(), secondMaterial.getAcademicMaterialId()));

        assertThat(reportedIds).containsExactly(firstMaterial.getAcademicMaterialId());
    }

    @Test
    void reportsCountCanBeIncrementedAtomically() {
        assertThat(materialRepository.incrementReportsCount(firstMaterial.getAcademicMaterialId())).isEqualTo(1);
        assertThat(materialRepository.incrementReportsCount(firstMaterial.getAcademicMaterialId())).isEqualTo(1);
        entityManager.clear();

        AcademicMaterial updated = materialRepository
                .findByAcademicMaterialIdAndActiveTrue(firstMaterial.getAcademicMaterialId()).orElseThrow();

        assertThat(updated.getReportsCount()).isEqualTo(2);
    }

    @Test
    void databaseRejectsSecondReportForSameMaterialAndReporter() {
        persist(report(firstMaterial, reporter));
        entityManager.flush();

        assertThatThrownBy(() -> {
            persist(report(firstMaterial, reporter));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private User user(String name, Role role) {
        return User.builder()
                .fullName(name)
                .email(name + "." + UUID.randomUUID() + "@report.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    private AcademicMaterial material(TutorSubject tutorSubject, String name) {
        return AcademicMaterial.builder()
                .tutorSubject(tutorSubject)
                .name(name)
                .storagePath(UUID.randomUUID() + "/" + name)
                .materialType(MaterialType.PDF)
                .active(true)
                .available(true)
                .build();
    }

    private AcademicMaterialReport report(AcademicMaterial material, User user) {
        return AcademicMaterialReport.builder()
                .material(material)
                .reporter(user)
                .reason(MaterialReportReason.OTHER)
                .description(null)
                .build();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}