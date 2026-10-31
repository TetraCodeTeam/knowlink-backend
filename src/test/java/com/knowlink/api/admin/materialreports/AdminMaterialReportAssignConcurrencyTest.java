package com.knowlink.api.admin.materialreports;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportCaseRepository;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two administrators take the same pending case at the same time: the conditional UPDATE must let
 * exactly one of them win and the case must end up assigned to that administrator.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminMaterialReportAssignConcurrencyTest {

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
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Career career;
    private User tutor;
    private User firstAdmin;
    private User secondAdmin;
    private TutorProfile tutorProfile;
    private Subject subject;
    private TutorSubject tutorSubject;
    private AcademicMaterial material;
    private MaterialReportCase reportCase;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        career = careerRepository.save(Career.builder().name("Career " + UUID.randomUUID()).build());
        tutor = userRepository.save(user("tutor", Role.TUTOR));
        firstAdmin = userRepository.save(user("admin-one", Role.ADMIN));
        secondAdmin = userRepository.save(user("admin-two", Role.ADMIN));
        tutorProfile = tutorProfileRepository.save(TutorProfile.builder().user(tutor).career(career).build());
        subject = subjectRepository.save(Subject.builder()
                .name("Subject " + UUID.randomUUID())
                .career(career)
                .isBasic(true)
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
                .name("material.pdf")
                .storagePath(UUID.randomUUID() + "/material.pdf")
                .materialType(MaterialType.PDF)
                .active(true)
                .available(true)
                .build());
        reportCase = caseRepository.save(MaterialReportCase.builder()
                .academicMaterial(material)
                .status(MaterialReportCaseStatus.PENDING)
                .openedAt(LocalDateTime.now().minusHours(2))
                .reportsCount(1)
                .build());
    }

    @AfterEach
    void cleanUp() {
        if (reportCase != null) {
            caseRepository.deleteById(reportCase.getMaterialReportCaseId());
        }
        if (material != null) {
            materialRepository.deleteById(material.getAcademicMaterialId());
        }
        if (tutorSubject != null) {
            tutorSubjectRepository.deleteById(tutorSubject.getTutorSubjectId());
        }
        if (subject != null) {
            subjectRepository.deleteById(subject.getSubjectId());
        }
        if (tutorProfile != null) {
            tutorProfileRepository.deleteById(tutorProfile.getTutorProfileId());
        }
        if (tutor != null) {
            userRepository.deleteById(tutor.getUserId());
        }
        if (firstAdmin != null) {
            userRepository.deleteById(firstAdmin.getUserId());
        }
        if (secondAdmin != null) {
            userRepository.deleteById(secondAdmin.getUserId());
        }
        if (career != null) {
            careerRepository.deleteById(career.getCareerId());
        }
    }

    @Test
    @DisplayName("Dos administradores toman el mismo caso: solo uno gana")
    void onlyOneAdminTakesThePendingCase() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> first = executor.submit(() -> assign(firstAdmin, start));
            Future<Attempt> second = executor.submit(() -> assign(secondAdmin, start));
            start.countDown();

            List<Attempt> attempts = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));

            assertThat(attempts).filteredOn(attempt -> attempt.updated() == 1).hasSize(1);
            Attempt loser = attempts.stream()
                    .filter(attempt -> attempt.updated() != 1)
                    .findFirst()
                    .orElseThrow();
            assertThat(loser.updated() == 0 || isConcurrencyConflict(loser.error())).isTrue();

            MaterialReportCase stored = caseRepository
                    .findDetailById(reportCase.getMaterialReportCaseId())
                    .orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(MaterialReportCaseStatus.IN_REVIEW);
            assertThat(stored.getAssignedAdmin()).isNotNull();
            assertThat(stored.getAssignedAdmin().getUserId())
                    .isIn(firstAdmin.getUserId(), secondAdmin.getUserId());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Attempt assign(User admin, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            Integer updated = transactionTemplate.execute(status -> caseRepository.assignIfPending(
                    reportCase.getMaterialReportCaseId(), admin, LocalDateTime.now()));
            return new Attempt(updated == null ? 0 : updated, null);
        } catch (Exception exception) {
            return new Attempt(-1, exception);
        }
    }

    private boolean isConcurrencyConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConcurrencyFailureException
                    || cause instanceof OptimisticLockException
                    || cause instanceof PessimisticLockException
                    || cause.getClass().getName().contains("JdbcSQLTransactionRollbackException")) {
                return true;
            }
        }
        return false;
    }

    private User user(String name, Role role) {
        return User.builder()
                .fullName(name)
                .email(name + "." + UUID.randomUUID() + "@concurrency.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    private record Attempt(int updated, Exception error) {
    }
}