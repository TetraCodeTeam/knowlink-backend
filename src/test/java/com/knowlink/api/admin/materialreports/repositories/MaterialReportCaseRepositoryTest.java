package com.knowlink.api.admin.materialreports.repositories;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.security.enums.Role;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MaterialReportCaseRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final LocalDateTime FROM = NOW.minusDays(7);
    private static final Pageable PAGE = PageRequest.of(0, 10, Sort.unsorted());

    @Autowired
    private IMaterialReportCaseRepository caseRepository;
    @Autowired
    private IAcademicMaterialRepository materialRepository;
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
    @PersistenceContext
    private EntityManager entityManager;

    private AcademicMaterial material;
    private User firstAdmin;
    private User secondAdmin;

    @BeforeEach
    void setUp() {
        Career career = careerRepository.save(Career.builder().name("Career " + UUID.randomUUID()).build());
        User tutor = userRepository.save(user("tutor", Role.TUTOR));
        TutorProfile tutorProfile = tutorProfileRepository.save(
                TutorProfile.builder().user(tutor).career(career).build());
        Subject subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID())
                .career(career)
                .isBasic(true)
                .build());
        TutorSubject tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
        material = materialRepository.save(AcademicMaterial.builder()
                .tutorSubject(tutorSubject)
                .name("material.pdf")
                .storagePath(UUID.randomUUID() + "/material.pdf")
                .materialType(MaterialType.PDF)
                .active(true)
                .available(true)
                .build());
        firstAdmin = userRepository.save(user("admin-one", Role.ADMIN));
        secondAdmin = userRepository.save(user("admin-two", Role.ADMIN));
    }

    @Test
    @DisplayName("Filtro por estado: solo devuelve los casos de ese estado dentro de la ventana")
    void findCasesByStatus_filtersStatusAndWindow() {
        persistCase(MaterialReportCaseStatus.PENDING, NOW.minusDays(1), null, null, 2);
        persistCase(MaterialReportCaseStatus.PENDING, NOW.minusDays(10), null, null, 1);
        persistCase(MaterialReportCaseStatus.IN_REVIEW, NOW.minusDays(2), firstAdmin, null, 3);
        flushAndClear();

        Page<MaterialReportCase> page = caseRepository.findCasesByStatus(
                MaterialReportCaseStatus.PENDING, FROM, NOW, PAGE);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(MaterialReportCaseStatus.PENDING);
        assertThat(page.getContent().get(0).getReportsCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Filtro assignedToMe: solo devuelve los casos del administrador indicado")
    void findCasesByStatusAssignedTo_filtersByAdmin() {
        persistCase(MaterialReportCaseStatus.IN_REVIEW, NOW.minusDays(1), firstAdmin, null, 1);
        persistCase(MaterialReportCaseStatus.IN_REVIEW, NOW.minusDays(1), secondAdmin, null, 1);
        persistCase(MaterialReportCaseStatus.IN_REVIEW, NOW.minusDays(1), null, null, 1);
        flushAndClear();

        Page<MaterialReportCase> page = caseRepository.findCasesByStatusAssignedTo(
                MaterialReportCaseStatus.IN_REVIEW, firstAdmin.getUserId(), FROM, NOW, PAGE);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getAssignedAdmin().getUserId())
                .isEqualTo(firstAdmin.getUserId());
    }

    @Test
    @DisplayName("Sin estado: devuelve todos los estados de la ventana ordenados por cantidad de denuncias")
    void findCasesAnyStatus_returnsAllStatusesOrdered() {
        persistCase(MaterialReportCaseStatus.PENDING, NOW.minusDays(3), null, null, 1);
        persistCase(MaterialReportCaseStatus.REMOVED, NOW.minusDays(2), firstAdmin, NOW.minusDays(1), 5);
        persistCase(MaterialReportCaseStatus.DISMISSED, NOW.minusDays(1), firstAdmin, NOW.minusHours(1), 3);
        persistCase(MaterialReportCaseStatus.PENDING, NOW.minusDays(30), null, null, 9);
        flushAndClear();

        Pageable sorted = PageRequest.of(0, 10, Sort.by(
                Sort.Order.desc("reportsCount"), Sort.Order.asc("openedAt")));
        Page<MaterialReportCase> page = caseRepository.findCasesAnyStatus(FROM, NOW, sorted);

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).extracting(MaterialReportCase::getReportsCount)
                .containsExactly(5, 3, 1);
    }

    @Test
    @DisplayName("Contadores por estado: la ventana es [from, to)")
    void countByStatusAndOpenedAt_respectsWindowBoundaries() {
        persistCase(MaterialReportCaseStatus.PENDING, FROM, null, null, 1);
        persistCase(MaterialReportCaseStatus.PENDING, FROM.minusSeconds(1), null, null, 1);
        persistCase(MaterialReportCaseStatus.PENDING, NOW, null, null, 1);
        persistCase(MaterialReportCaseStatus.PENDING, NOW.minusHours(1), null, null, 1);
        flushAndClear();

        assertThat(caseRepository.countByStatusAndOpenedAt(MaterialReportCaseStatus.PENDING, FROM, NOW))
                .isEqualTo(2);
        assertThat(caseRepository.countByStatusAndOpenedAtAssignedTo(
                MaterialReportCaseStatus.PENDING, firstAdmin.getUserId(), FROM, NOW))
                .isZero();
    }

    @Test
    @DisplayName("Casos abiertos del material: solo PENDING/IN_REVIEW, el más reciente primero")
    void findOpenCasesByMaterialId_returnsOpenCasesNewestFirst() {
        persistCase(MaterialReportCaseStatus.PENDING, NOW.minusDays(5), null, null, 1);
        persistCase(MaterialReportCaseStatus.IN_REVIEW, NOW.minusDays(2), firstAdmin, null, 1);
        persistCase(MaterialReportCaseStatus.REMOVED, NOW.minusDays(1), firstAdmin, NOW, 1);
        flushAndClear();

        List<MaterialReportCase> openCases = caseRepository.findOpenCasesByMaterialId(
                material.getAcademicMaterialId());

        assertThat(openCases).hasSize(2);
        assertThat(openCases).extracting(MaterialReportCase::getStatus)
                .containsExactly(MaterialReportCaseStatus.IN_REVIEW, MaterialReportCaseStatus.PENDING);
    }

    private void persistCase(MaterialReportCaseStatus status, LocalDateTime openedAt, User assignedAdmin,
            LocalDateTime resolvedAt, int reportsCount) {
        entityManager.persist(MaterialReportCase.builder()
                .academicMaterial(material)
                .status(status)
                .assignedAdmin(assignedAdmin)
                .assignedAt(assignedAdmin == null ? null : openedAt.plusMinutes(5))
                .openedAt(openedAt)
                .resolvedAt(resolvedAt)
                .reportsCount(reportsCount)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private User user(String name, Role role) {
        return User.builder()
                .fullName(name)
                .email(name + "." + UUID.randomUUID() + "@case.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }
}