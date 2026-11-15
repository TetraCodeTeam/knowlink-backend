package com.knowlink.api.tutors.statistics.repositories;

import com.knowlink.api.tutors.data.models.Institution;
import com.knowlink.api.tutors.repositories.IInstitutionRepository;
import com.knowlink.api.testsupport.CatalogFixtures;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.data.enums.RefundPolicy;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.data.models.BookingCancellation;
import com.knowlink.api.claims.data.enums.ClaimReason;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.payments.data.models.FundsTransfer;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.statistics.data.models.OfferedSlotHistory;
import com.knowlink.api.tutors.statistics.data.projections.ActiveClaimRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorBookingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorRatingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorSlotRow;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TutorStatisticsRepositoryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final LocalDate IN_PERIOD = LocalDate.of(2026, 10, 10);

    @Autowired
    private ITutorStatisticsRepository repository;

    @Autowired
    private IInstitutionRepository institutionRepository;

    private Institution institution;
    @PersistenceContext
    private EntityManager entityManager;

    private User tutor;
    private User student;
    private User otherTutor;
    private TutorProfile tutorProfile;
    private TutorSubject tutorSubject;
    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        institution = CatalogFixtures.saveInstitution(institutionRepository, "Test " + System.nanoTime());
        Career career = persist(Career.builder().institution(institution).name("Career " + UUID.randomUUID()).build());
        tutor = persist(user("tutor", Role.TUTOR));
        otherTutor = persist(user("other-tutor", Role.TUTOR));
        student = persist(user("student", Role.STUDENT));
        tutorProfile = persist(TutorProfile.builder().user(tutor).career(career).build());
        Subject subject = persist(Subject.builder().institution(institution)
                .name("Subject " + UUID.randomUUID()).career(career).build());
        tutorSubject = persist(TutorSubject.builder()
                .tutorProfile(tutorProfile)
                .subject(subject)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .build());
        slot = persist(slot(IN_PERIOD, LocalTime.of(9, 0), LocalTime.of(12, 0)));
    }

    @Test
    void bookingRowsJoinFundsAndCancellationWhenTheyExist() {
        Booking completed = persist(booking(BookingStatus.COMPLETED, IN_PERIOD));
        persist(FundsTransfer.builder()
                .booking(completed)
                .originalAmount(new BigDecimal("1030.00"))
                .systemRetentionPercentage(BigDecimal.ZERO)
                .transferredAmount(new BigDecimal("1000.00"))
                .recipient(FundsRecipient.TUTOR)
                .concept("Liberacion por sesion completada")
                .fundsStatus(FundsStatus.RELEASED_TO_TUTOR)
                .build());
        Booking cancelled = persist(booking(BookingStatus.CANCELLED, IN_PERIOD.plusDays(1)));
        persist(BookingCancellation.builder()
                .booking(cancelled)
                .cancelledBy(CancellationRole.STUDENT)
                .refundDestination(RefundDestination.TUTOR)
                .refundPolicy(RefundPolicy.TRANSFER_TOTAL_TUTOR)
                .amount(new BigDecimal("1000.00"))
                .hoursInAdvance(5L)
                .build());
        Booking plain = persist(booking(BookingStatus.BOOKED, IN_PERIOD.plusDays(2)));
        entityManager.flush();
        entityManager.clear();

        List<TutorBookingRow> rows = repository.findBookingRows(tutor.getUserId(), FROM, TO);

        assertThat(rows).hasSize(3);
        TutorBookingRow completedRow = row(rows, completed);
        assertThat(completedRow.fundsStatus()).isEqualTo(FundsStatus.RELEASED_TO_TUTOR);
        assertThat(completedRow.fundsRecipient()).isEqualTo(FundsRecipient.TUTOR);
        assertThat(completedRow.transferredAmount()).isEqualByComparingTo("1000.00");
        assertThat(completedRow.cancelledBy()).isNull();
        assertThat(completedRow.studentId()).isEqualTo(student.getUserId());
        assertThat(completedRow.tutorSubjectId()).isEqualTo(tutorSubject.getTutorSubjectId());

        TutorBookingRow cancelledRow = row(rows, cancelled);
        assertThat(cancelledRow.cancelledBy()).isEqualTo(CancellationRole.STUDENT);
        assertThat(cancelledRow.refundPolicy()).isEqualTo(RefundPolicy.TRANSFER_TOTAL_TUTOR);
        assertThat(cancelledRow.fundsStatus()).isNull();

        assertThat(row(rows, plain).fundsStatus()).isNull();
    }

    @Test
    void bookingRowsAreScopedToTheTutorAndThePeriod() {
        persist(booking(BookingStatus.COMPLETED, IN_PERIOD));
        persist(booking(BookingStatus.COMPLETED, FROM.minusDays(1)));
        persist(booking(BookingStatus.COMPLETED, TO.plusDays(1)));
        persist(booking(BookingStatus.COMPLETED, FROM));
        persist(booking(BookingStatus.COMPLETED, TO));
        entityManager.flush();

        assertThat(repository.findBookingRows(tutor.getUserId(), FROM, TO)).hasSize(3);
        assertThat(repository.findBookingRows(otherTutor.getUserId(), FROM, TO)).isEmpty();
    }

    // ── Cancelaciones de clases posteriores al fin del período ───────────────

    @Test
    void cancellationMadeInThePeriodForALaterClassIsIncluded() {
        // El período termina hoy: una clase de mañana cancelada hoy no puede quedar afuera.
        Booking tomorrow = persist(booking(BookingStatus.CANCELLED, TO.plusDays(1)));
        cancel(tomorrow, TO.atTime(12, 0));
        entityManager.flush();

        List<TutorBookingRow> rows = repository.findBookingRows(tutor.getUserId(), FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).bookingId()).isEqualTo(tomorrow.getBookingId());
        assertThat(rows.get(0).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(rows.get(0).cancelledBy()).isEqualTo(CancellationRole.STUDENT);
    }

    @Test
    void cancellationMadeBeforeThePeriodForALaterClassIsNotIncluded() {
        // Se canceló antes de que empiece el período: cuenta cuando llegue la fecha de la clase.
        Booking later = persist(booking(BookingStatus.CANCELLED, TO.plusDays(1)));
        cancel(later, FROM.minusDays(1).atTime(10, 0));
        entityManager.flush();

        assertThat(repository.findBookingRows(tutor.getUserId(), FROM, TO)).isEmpty();
    }

    @Test
    void cancellationMadeAfterThePeriodForALaterClassIsNotIncluded() {
        Booking later = persist(booking(BookingStatus.CANCELLED, TO.plusDays(3)));
        cancel(later, TO.plusDays(1).atTime(8, 0));
        entityManager.flush();

        assertThat(repository.findBookingRows(tutor.getUserId(), FROM, TO)).isEmpty();
    }

    @Test
    void aLaterClassThatIsNotCancelledIsNotIncluded() {
        persist(booking(BookingStatus.BOOKED, TO.plusDays(1)));
        persist(booking(BookingStatus.COMPLETED, TO.plusDays(1)));
        entityManager.flush();

        assertThat(repository.findBookingRows(tutor.getUserId(), FROM, TO)).isEmpty();
    }

    @Test
    void cancelledClassesInsideThePeriodAreReturnedOnceWhateverWhenTheyWereCancelled() {
        Booking cancelledInPeriod = persist(booking(BookingStatus.CANCELLED, IN_PERIOD));
        cancel(cancelledInPeriod, IN_PERIOD.minusDays(1).atTime(9, 0));
        // Cancelada antes del período, pero la clase cae adentro: cuenta por la fecha de la clase.
        Booking cancelledEarlier = persist(booking(BookingStatus.CANCELLED, IN_PERIOD.plusDays(2)));
        cancel(cancelledEarlier, FROM.minusDays(3).atTime(9, 0));
        entityManager.flush();

        List<TutorBookingRow> rows = repository.findBookingRows(tutor.getUserId(), FROM, TO);

        assertThat(rows).extracting(TutorBookingRow::bookingId)
                .containsExactlyInAnyOrder(cancelledInPeriod.getBookingId(), cancelledEarlier.getBookingId());
    }

    @Test
    void lateCancellationsOfAnotherTutorAreNotIncluded() {
        Booking mine = persist(booking(BookingStatus.CANCELLED, TO.plusDays(1)));
        cancel(mine, TO.atTime(12, 0));
        entityManager.flush();

        assertThat(repository.findBookingRows(otherTutor.getUserId(), FROM, TO)).isEmpty();
    }

    // ── Reclamos activos detrás de los fondos retenidos ──────────────────────

    @Test
    void activeClaimRowsReturnOnlyOpenOrInReviewClaimsOldestFirst() {
        Booking disputed = persist(booking(BookingStatus.NOT_CONFIRMED, IN_PERIOD));
        Booking resolvedOnly = persist(booking(BookingStatus.NOT_CONFIRMED, IN_PERIOD.plusDays(1)));
        Booking notAsked = persist(booking(BookingStatus.NOT_CONFIRMED, IN_PERIOD.plusDays(2)));
        SessionClaim newer = claim(disputed, ClaimStatus.OPEN, IN_PERIOD.plusDays(3).atTime(10, 0));
        SessionClaim older = claim(disputed, ClaimStatus.IN_REVIEW, IN_PERIOD.plusDays(2).atTime(10, 0));
        claim(disputed, ClaimStatus.RESOLVED, IN_PERIOD.plusDays(1).atTime(10, 0));
        claim(resolvedOnly, ClaimStatus.RESOLVED, IN_PERIOD.plusDays(2).atTime(11, 0));
        claim(notAsked, ClaimStatus.OPEN, IN_PERIOD.plusDays(2).atTime(12, 0));
        entityManager.flush();

        List<ActiveClaimRow> rows = repository.findActiveClaimRows(
                List.of(disputed.getBookingId(), resolvedOnly.getBookingId()), ClaimStatus.ACTIVE_STATUSES);

        // El resuelto y la reserva que no se pidió quedan afuera; dentro de una reserva, el más antiguo primero.
        assertThat(rows).containsExactly(
                new ActiveClaimRow(disputed.getBookingId(), older.getClaimId()),
                new ActiveClaimRow(disputed.getBookingId(), newer.getClaimId()));
    }

    @Test
    void countByStatusOnlyCountsTheRequestedStatusInTheRange() {
        persist(booking(BookingStatus.COMPLETED, IN_PERIOD));
        persist(booking(BookingStatus.COMPLETED, IN_PERIOD.plusDays(1)));
        persist(booking(BookingStatus.CANCELLED, IN_PERIOD));
        persist(booking(BookingStatus.COMPLETED, FROM.minusDays(3)));
        entityManager.flush();

        assertThat(repository.countByStatus(tutor.getUserId(), FROM, TO, BookingStatus.COMPLETED)).isEqualTo(2);
    }

    @Test
    void onlyVisibleRatingsOfCompletedSessionsReceivedByTheTutorAreReturned() {
        Booking completed = persist(booking(BookingStatus.COMPLETED, IN_PERIOD));
        Booking hidden = persist(booking(BookingStatus.COMPLETED, IN_PERIOD.plusDays(1)));
        Booking cancelled = persist(booking(BookingStatus.CANCELLED, IN_PERIOD.plusDays(2)));
        persist(rating(completed, tutor, student, 5, true));
        persist(rating(completed, student, tutor, 3, true));
        persist(rating(hidden, tutor, student, 1, false));
        persist(rating(cancelled, tutor, student, 4, true));
        entityManager.flush();

        List<TutorRatingRow> ratings = repository.findVisibleRatingRows(tutor.getUserId(), FROM, TO);

        assertThat(ratings).containsExactly(new TutorRatingRow(5, tutorSubject.getTutorSubjectId()));
    }

    @Test
    void liveAndArchivedSlotsAreReturnedForTheTutorProfileInRange() {
        persist(OfferedSlotHistory.builder()
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .date(IN_PERIOD.minusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .build());
        persist(OfferedSlotHistory.builder()
                .tutorProfileId(UUID.randomUUID())
                .date(IN_PERIOD)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .build());
        entityManager.flush();

        List<TutorSlotRow> live = repository.findLiveSlotRows(tutorProfile.getTutorProfileId(), FROM, TO);
        List<TutorSlotRow> archived = repository.findArchivedSlotRows(tutorProfile.getTutorProfileId(), FROM, TO);

        assertThat(live).containsExactly(new TutorSlotRow(IN_PERIOD, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        assertThat(archived).containsExactly(
                new TutorSlotRow(IN_PERIOD.minusDays(2), LocalTime.of(14, 0), LocalTime.of(16, 0)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TutorBookingRow row(List<TutorBookingRow> rows, Booking booking) {
        return rows.stream()
                .filter(row -> row.bookingId().equals(booking.getBookingId()))
                .findFirst()
                .orElseThrow();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    /**
     * La fecha de la cancelación la fija @PrePersist con la hora actual y no es actualizable por la
     * entidad: se corrige con una actualización masiva para poder probar los límites del período.
     */
    private BookingCancellation cancel(Booking booking, LocalDateTime cancelledAt) {
        BookingCancellation cancellation = persist(BookingCancellation.builder()
                .booking(booking)
                .cancelledBy(CancellationRole.STUDENT)
                .refundDestination(RefundDestination.STUDENT)
                .refundPolicy(RefundPolicy.REFUND_TOTAL_STUDENT)
                .amount(new BigDecimal("1000.00"))
                .hoursInAdvance(30L)
                .build());
        entityManager.flush();
        entityManager.createQuery(
                        "UPDATE BookingCancellation bc SET bc.createdAt = :at WHERE bc.cancellationId = :id")
                .setParameter("at", cancelledAt)
                .setParameter("id", cancellation.getCancellationId())
                .executeUpdate();
        return cancellation;
    }

    /** Igual que {@link #cancel}: la fecha de creación se fija con una actualización masiva. */
    private SessionClaim claim(Booking booking, ClaimStatus status, LocalDateTime createdAt) {
        SessionClaim claim = persist(SessionClaim.builder()
                .booking(booking)
                .claimant(student)
                .claimantRole(Role.STUDENT)
                .reason(ClaimReason.TUTOR_COULD_NOT_ATTEND)
                .status(status)
                .build());
        entityManager.flush();
        entityManager.createQuery("UPDATE SessionClaim c SET c.createdAt = :at WHERE c.claimId = :id")
                .setParameter("at", createdAt)
                .setParameter("id", claim.getClaimId())
                .executeUpdate();
        return claim;
    }

    private User user(String name, Role role) {
        return User.builder()
                .fullName(name)
                .email(name + "." + UUID.randomUUID() + "@stats.test")
                .password("hashed")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    private TimeSlot slot(LocalDate date, LocalTime start, LocalTime end) {
        return TimeSlot.builder()
                .date(date)
                .startTime(start)
                .endTime(end)
                .status(SlotStatus.AVAILABLE)
                .tutorProfileId(tutorProfile.getTutorProfileId())
                .build();
    }

    /** Cada reserva usa su propio slot: (time_slot_id, start_time) es único. */
    private Booking booking(BookingStatus status, LocalDate date) {
        TimeSlot ownSlot = persist(slot(date, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        return Booking.builder()
                .amount(new BigDecimal("1000.00"))
                .sessionDate(date)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .modality(Modality.VIRTUAL)
                .bookingStatus(status)
                .timeSlot(ownSlot)
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutor)
                .build();
    }

    private Rating rating(Booking booking, User rated, User rater, int score, boolean visible) {
        return Rating.builder()
                .booking(booking)
                .ratedUser(rated)
                .raterUser(rater)
                .score(score)
                .ratingDate(LocalDateTime.of(2026, 10, 11, 10, 0))
                .visible(visible)
                .build();
    }
}
