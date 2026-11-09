package com.knowlink.api.tutors.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.repositories.NotificationRepository;
import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.ratings.services.implementations.RatingReputationServiceImpl;
import com.knowlink.api.ratings.services.interfaces.IRatingService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ICareerRepository;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.tutors.repositories.ISubjectRepository;
import com.knowlink.api.tutors.repositories.ITutorProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.tutors.services.interfaces.ISubjectVerificationService;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * End to end recalculation of the derived subject verification: a rating that
 * becomes visible has to verify (or unverify) the subject right away, both on
 * the direct submission path and through the 60s reveal job.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubjectVerificationIntegrationTest {

    private static final LocalDate SESSION_DATE = LocalDate.now().minusDays(3);

    @Autowired
    private ISubjectVerificationService subjectVerificationService;
    @Autowired
    private IRatingService ratingService;
    @Autowired
    private RatingReputationServiceImpl ratingReputationService;
    @Autowired
    private IInstitutionRepository institutionRepository;
    @Autowired
    private ICareerRepository careerRepository;
    @Autowired
    private ISubjectRepository subjectRepository;
    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private ITutorProfileRepository tutorProfileRepository;
    @Autowired
    private ITutorSubjectRepository tutorSubjectRepository;
    @Autowired
    private IRatingRepository ratingRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private String suffix;
    private UUID institutionId;
    private UUID careerId;
    private UUID subjectId;
    private User tutorUser;
    private User studentUser;
    private TutorSubject tutorSubject;

    @BeforeEach
    void seed() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> seedData());
    }

    private void seedData() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Institution institution = CatalogFixtures.saveInstitution(institutionRepository, "Verify " + suffix);
        institutionId = institution.getInstitutionId();
        Career career = careerRepository.save(Career.builder()
                .institution(institution)
                .name("Ingeniería " + suffix)
                .build());
        careerId = career.getCareerId();
        Subject subject = subjectRepository.save(Subject.builder()
                .institution(institution)
                .name("Álgebra " + suffix)
                .career(career)
                .build());
        subjectId = subject.getSubjectId();

        tutorUser = saveUser("Tutor " + suffix, "tutor." + suffix + "@verify.test", Role.TUTOR);
        studentUser = saveUser("Student " + suffix, "student." + suffix + "@verify.test", Role.STUDENT);

        TutorProfile tutorProfile = tutorProfileRepository.save(TutorProfile.builder()
                .user(tutorUser)
                .career(career)
                .build());
        tutorSubject = tutorSubjectRepository.save(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .modality(Modality.VIRTUAL)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.PENDING)
                .build());
    }

    @Test
    @DisplayName("Una calificación visible enviada por el alumno verifica la materia")
    /**
     * This test is not @Transactional on purpose (notifications commit in their own
     * transaction), so every fixture has to be removed by hand: other tests assert
     * global row counts and would see these bookings.
     */
    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID tutorProfileId = tutorSubject.getTutorProfile().getTutorProfileId();

            bulkDelete("DELETE FROM Rating r WHERE r.booking.tutorSubject.tutorSubjectId = :id",
                    "id", tutorSubject.getTutorSubjectId());
            bulkDelete("DELETE FROM Booking b WHERE b.tutorSubject.tutorSubjectId = :id",
                    "id", tutorSubject.getTutorSubjectId());
            bulkDelete("DELETE FROM TimeSlot t WHERE t.tutorProfileId = :id",
                    "id", tutorProfileId);
            bulkDelete("DELETE FROM Notification n WHERE n.recipient.userId = :id",
                    "id", tutorUser.getUserId());
            bulkDelete("DELETE FROM TutorSubject ts WHERE ts.tutorSubjectId = :id",
                    "id", tutorSubject.getTutorSubjectId());
            bulkDelete("DELETE FROM TutorProfile tp WHERE tp.user.userId = :id",
                    "id", tutorUser.getUserId());
            bulkDelete("DELETE FROM Subject s WHERE s.career.careerId = :id",
                    "id", careerId);
            bulkDelete("DELETE FROM Career c WHERE c.institution.institutionId = :id",
                    "id", institutionId);
            bulkDelete("DELETE FROM User u WHERE u.userId = :id", "id", studentUser.getUserId());
            bulkDelete("DELETE FROM User u WHERE u.userId = :id", "id", tutorUser.getUserId());
            bulkDelete("DELETE FROM Institution i WHERE i.institutionId = :id",
                    "id", institutionId);
        });
    }

    private void bulkDelete(String jpql, String parameter, Object value) {
        entityManager.createQuery(jpql).setParameter(parameter, value).executeUpdate();
    }
    void submittingAVisibleRatingVerifiesTheSubject() {
        Booking booking = persistCompletedBooking();

        ratingService.submitRating(studentUser.getUserId(), booking.getBookingId(),
                new CreateRatingRequest(5, null));

        TutorSubject reloaded = reloadSubject();
        assertThat(reloaded.isVerified()).isTrue();
        assertThat(reloaded.getRatingCount()).isEqualTo(1);
        assertThat(reloaded.getAverageRating()).isEqualByComparingTo("5.00");
        assertThat(reloaded.getTutorSubjectStatus()).isEqualTo(TutorSubjectStatus.PENDING);
        assertThat(reloadTutorProfile().isVerified()).isTrue();
        assertThat(notificationRepository.findByRecipient_UserIdAndTypeAndResourceId(
                tutorUser.getUserId(), NotificationType.SUBJECT_VALIDATION_UPDATED,
                tutorSubject.getTutorSubjectId())).isPresent();
    }

    @Test
    @DisplayName("El job de revelado calificaciones verifica la materia recién cuando la reseña se hace visible")
    void revealJobVerifiesTheSubjectOnceTheRatingBecomesVisible() {
        Booking booking = persistCompletedBooking();
        persistRating(booking, studentUser, tutorUser, 5, false);

        subjectVerificationService.recalculateForTutor(tutorUser.getUserId());

        assertThat(reloadSubject().isVerified()).isFalse();
        assertThat(reloadSubject().getRatingCount()).isZero();

        ratingReputationService.revealExpiredRatings();

        // The job worked on its own copy: read the rating back instead of the stale instance.
        Rating revealed = ratingRepository.findByBookingBookingId(booking.getBookingId()).get(0);
        assertThat(revealed.isVisible()).isTrue();
        TutorSubject reloaded = reloadSubject();
        assertThat(reloaded.isVerified()).isTrue();
        assertThat(reloaded.getRatingCount()).isEqualTo(1);
        assertThat(reloadTutorProfile().isVerified()).isTrue();
    }

    @Test
    @DisplayName("Calificaciones que no cuentan no verifican la materia")
    void ratingsThatDoNotCountLeaveTheSubjectUnverified() {
        Booking booking = persistCompletedBooking();
        // Hidden rating: still inside the blind review window.
        persistRating(booking, studentUser, tutorUser, 5, false);

        // A rating the tutor gave as a student of somebody else's booking.
        Booking otherBooking = persistCompletedBooking();
        persistRating(otherBooking, tutorUser, studentUser, 5, true);

        subjectVerificationService.recalculateForTutor(tutorUser.getUserId());

        assertThat(reloadSubject().isVerified()).isFalse();
        assertThat(reloadSubject().getRatingCount()).isZero();
        assertThat(reloadSubject().getAverageRating()).isNull();
    }

    private TutorSubject reloadSubject() {
        return tutorSubjectRepository.findById(tutorSubject.getTutorSubjectId()).orElseThrow();
    }

    private TutorProfile reloadTutorProfile() {
        return tutorProfileRepository.findById(
                tutorSubject.getTutorProfile().getTutorProfileId()).orElseThrow();
    }

    private User saveUser(String fullName, String email, Role role) {
        return userRepository.save(User.builder()
                .fullName(fullName)
                .email(email)
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Booking persistCompletedBooking() {
        return new TransactionTemplate(transactionManager).execute(status -> persistCompletedBookingWithinTransaction());
    }

    private Booking persistCompletedBookingWithinTransaction() {
        TutorProfile tutorProfile = tutorSubject.getTutorProfile();
        LocalTime endTime = LocalTime.of(12, 0);
        TimeSlot timeSlot = TimeSlot.builder()
                .date(SESSION_DATE)
                .startTime(endTime.minusHours(1))
                .endTime(endTime)
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build();
        entityManager.persist(timeSlot);
        Booking booking = Booking.builder()
                .amount(BigDecimal.TEN)
                .sessionDate(SESSION_DATE)
                .startTime(endTime.minusHours(1))
                .endTime(endTime)
                .modality(Modality.VIRTUAL)
                .bookingStatus(BookingStatus.COMPLETED)
                .timeSlot(timeSlot)
                .tutorSubject(tutorSubject)
                .student(studentUser)
                .tutor(tutorUser)
                .build();
        entityManager.persist(booking);
        entityManager.flush();
        return booking;
    }

    private Rating persistRating(Booking booking, User rater, User rated, int score, boolean visible) {
        return new TransactionTemplate(transactionManager).execute(status -> persistRatingWithinTransaction(booking, rater, rated, score, visible));
    }

    private Rating persistRatingWithinTransaction(Booking booking, User rater, User rated, int score, boolean visible) {
        Rating rating = Rating.builder()
                .booking(booking)
                .raterUser(rater)
                .ratedUser(rated)
                .score(score)
                .ratingDate(LocalDateTime.of(2026, 10, 1, 12, 0))
                .visible(visible)
                .build();
        entityManager.persist(rating);
        entityManager.flush();
        return rating;
    }
}