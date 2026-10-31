package com.knowlink.api.admin.claims;

import com.knowlink.api.admin.claims.repositories.IAdminSessionClaimRepository;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two administrators take the same open claim dispute at the same time: the conditional UPDATE must
 * let exactly one of them win and the dispute must end up assigned to that administrator.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminSessionClaimAssignConcurrencyTest {

    @Autowired
    private IAdminSessionClaimRepository adminSessionClaimRepository;
    @Autowired
    private IBookingRepository bookingRepository;
    @Autowired
    private ITimeSlotRepository timeSlotRepository;
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
    private User student;
    private User firstAdmin;
    private User secondAdmin;
    private TutorProfile tutorProfile;
    private Subject subject;
    private TutorSubject tutorSubject;
    private TimeSlot timeSlot;
    private Booking booking;
    private SessionClaim claim;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        career = careerRepository.save(Career.builder().name("Career " + UUID.randomUUID()).build());
        tutor = userRepository.save(user("tutor", Role.TUTOR));
        student = userRepository.save(user("student", Role.STUDENT));
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

        LocalDate sessionDate = LocalDate.now().minusDays(1);
        timeSlot = timeSlotRepository.save(TimeSlot.builder()
                .date(sessionDate)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build());
        booking = bookingRepository.save(Booking.builder()
                .amount(BigDecimal.TEN)
                .sessionDate(sessionDate)
                .startTime(timeSlot.getStartTime())
                .endTime(timeSlot.getEndTime())
                .modality(Modality.VIRTUAL)
                .bookingStatus(BookingStatus.COMPLETED)
                .confirmedAt(LocalDateTime.now().minusDays(1))
                .timeSlot(timeSlot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build());
        claim = adminSessionClaimRepository.save(SessionClaim.builder()
                .booking(booking)
                .claimant(student)
                .claimantRole(Role.STUDENT)
                .reason(ClaimReason.TUTOR_COULD_NOT_ATTEND)
                .status(ClaimStatus.OPEN)
                .build());
        claim.setDisputeId(claim.getClaimId());
        claim = adminSessionClaimRepository.save(claim);
    }

    @AfterEach
    void cleanUp() {
        if (claim != null) {
            adminSessionClaimRepository.deleteById(claim.getClaimId());
        }
        if (booking != null) {
            bookingRepository.deleteById(booking.getBookingId());
        }
        if (timeSlot != null) {
            timeSlotRepository.deleteById(timeSlot.getTimeSlotId());
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
        for (User user : new User[] { student, tutor, firstAdmin, secondAdmin }) {
            if (user != null) {
                userRepository.deleteById(user.getUserId());
            }
        }
        if (career != null) {
            careerRepository.deleteById(career.getCareerId());
        }
    }

    @Test
    @DisplayName("Dos administradores toman la misma disputa: solo uno gana")
    void onlyOneAdminTakesTheOpenDispute() throws Exception {
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

            SessionClaim stored = adminSessionClaimRepository.findByIdWithDetails(claim.getClaimId())
                    .orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(ClaimStatus.IN_REVIEW);
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
            Integer updated = transactionTemplate.execute(status -> adminSessionClaimRepository.assignIfOpen(
                    claim.getClaimId(), admin, LocalDateTime.now()));
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