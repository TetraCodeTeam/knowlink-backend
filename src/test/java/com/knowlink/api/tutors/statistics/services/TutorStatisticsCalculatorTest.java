package com.knowlink.api.tutors.statistics.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundPolicy;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.statistics.controllers.responses.OccupancyCellResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.SubjectPerformanceResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.TutorStatisticsResponse;
import com.knowlink.api.tutors.statistics.data.enums.AvailabilityBand;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;
import com.knowlink.api.tutors.statistics.data.projections.TutorBookingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorRatingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorSlotRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorStatisticsInput;
import com.knowlink.api.tutors.statistics.services.implementations.TutorStatisticsCalculator;
import com.knowlink.api.tutors.statistics.utils.StatisticsPeriod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TutorStatisticsCalculatorTest {

    private static final StatisticsPeriod PERIOD = new StatisticsPeriod(
            StatisticsPeriodType.LAST_3_MONTHS, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 22));
    // 2026-10-05 es lunes.
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    private final TutorStatisticsCalculator calculator = new TutorStatisticsCalculator();

    private final UUID paidSubjectId = UUID.randomUUID();
    private final UUID freeSubjectId = UUID.randomUUID();
    private final UUID deactivatedSubjectId = UUID.randomUUID();
    private final List<TutorSubject> subjects = List.of(
            subject(paidSubjectId, "Análisis de Sistemas", CompensationType.PAID, TutorSubjectStatus.ACTIVE, true),
            subject(freeSubjectId, "Física 1", CompensationType.FREE, TutorSubjectStatus.ACTIVE, true),
            subject(deactivatedSubjectId, "Lean Ágil", CompensationType.FREE, TutorSubjectStatus.REJECTED, false));

    @Test
    void fulfillmentRateExcludesCancellationsAndCountsUnheldSessions() {
        List<TutorBookingRow> bookings = List.of(
                booking(BookingStatus.COMPLETED, MONDAY),
                booking(BookingStatus.COMPLETED, MONDAY),
                booking(BookingStatus.COMPLETED, MONDAY),
                booking(BookingStatus.NOT_FULFILLED_BY_TUTOR, MONDAY),
                booking(BookingStatus.CANCELLED, MONDAY),
                booking(BookingStatus.CANCELLED, MONDAY));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.summary().completedSessions()).isEqualTo(3);
        assertThat(response.summary().eligibleSessions()).isEqualTo(4);
        assertThat(response.summary().fulfillmentRate()).isEqualTo(75.0);
    }

    @Test
    void fulfillmentRateIsNullWithoutEligibleSessions() {
        TutorStatisticsResponse response = calculate(
                List.of(booking(BookingStatus.CANCELLED, MONDAY)), List.of(), List.of());

        assertThat(response.summary().fulfillmentRate()).isNull();
        assertThat(response.hasActivity()).isTrue();
    }

    @Test
    void noBookingsMeansNoActivity() {
        TutorStatisticsResponse response = calculate(List.of(), List.of(), List.of());

        assertThat(response.hasActivity()).isFalse();
        assertThat(response.summary().completedSessions()).isZero();
        assertThat(response.summary().averageRating()).isNull();
    }

    @Test
    void expiredAndPendingBookingsAreNotActivity() {
        TutorStatisticsResponse response = calculate(List.of(
                booking(BookingStatus.PENDING, MONDAY),
                booking(BookingStatus.EXPIRED, MONDAY)), List.of(), List.of());

        assertThat(response.hasActivity()).isFalse();
    }

    @Test
    void cancellationsAreSplitByWhoCancelledAndCreditedOnes() {
        List<TutorBookingRow> bookings = List.of(
                cancelled(CancellationRole.TUTOR, RefundPolicy.REFUND_TOTAL_STUDENT),
                cancelled(CancellationRole.STUDENT, RefundPolicy.REFUND_TOTAL_STUDENT),
                cancelled(CancellationRole.STUDENT, RefundPolicy.TRANSFER_TOTAL_TUTOR),
                cancelled(CancellationRole.STUDENT, RefundPolicy.TRANSFER_TOTAL_TUTOR),
                booking(BookingStatus.SESSION_NOT_HELD, MONDAY),
                booking(BookingStatus.NOT_FULFILLED_BY_TUTOR, MONDAY));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.unheldSessions().cancelledByTutor()).isEqualTo(1);
        assertThat(response.unheldSessions().cancelledByStudent()).isEqualTo(3);
        assertThat(response.unheldSessions().cancelledByStudentCredited()).isEqualTo(2);
        assertThat(response.unheldSessions().notHeld()).isEqualTo(2);
    }

    @Test
    void monthlySessionsIncludesEveryMonthOfThePeriodEvenWithZero() {
        List<TutorBookingRow> bookings = List.of(
                booking(BookingStatus.COMPLETED, LocalDate.of(2026, 8, 10)),
                booking(BookingStatus.COMPLETED, LocalDate.of(2026, 10, 3)),
                booking(BookingStatus.COMPLETED, LocalDate.of(2026, 10, 4)));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.monthlySessions()).extracting("month", "completedSessions")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2026-08", 1),
                        org.assertj.core.groups.Tuple.tuple("2026-09", 0),
                        org.assertj.core.groups.Tuple.tuple("2026-10", 2));
    }

    @Test
    void netIncomeAddsReleasedAndPendingButNotWithheldFunds() {
        List<TutorBookingRow> bookings = List.of(
                funded(BookingStatus.COMPLETED, FundsStatus.RELEASED_TO_TUTOR, "1030.00", "1000.00"),
                funded(BookingStatus.COMPLETED, FundsStatus.HELD, "2060.00", "0.00"),
                funded(BookingStatus.COMPLETED, FundsStatus.SUSPENDED_BY_CLAIM, "4120.00", "0.00"));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.summary().netIncome()).isEqualByComparingTo("3000.00");
        assertThat(response.funds().released().amount()).isEqualByComparingTo("1000.00");
        assertThat(response.funds().released().sessions()).isEqualTo(1);
        assertThat(response.funds().pendingRelease().amount()).isEqualByComparingTo("2000.00");
        assertThat(response.funds().withheldByClaim().amount()).isEqualByComparingTo("4000.00");
        assertThat(response.funds().withheldByClaim().sessions()).isEqualTo(1);
    }

    @Test
    void heldFundsOfCancelledBookingsAreNotPending() {
        List<TutorBookingRow> bookings = List.of(
                funded(BookingStatus.CANCELLED, FundsStatus.HELD, "1000.00", "0.00"),
                funded(BookingStatus.EXPIRED, FundsStatus.HELD, "1000.00", "0.00"));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.funds().pendingRelease().sessions()).isZero();
        assertThat(response.summary().netIncome()).isEqualByComparingTo("0");
    }

    @Test
    void refundedFundsAndFreeSessionsDoNotCountAsIncome() {
        List<TutorBookingRow> bookings = List.of(
                funded(BookingStatus.NOT_FULFILLED_BY_TUTOR, FundsStatus.REFUNDED_TO_STUDENT, "1000.00", "1000.00"),
                funded(BookingStatus.COMPLETED, FundsStatus.RELEASED_TO_TUTOR, "0.00", "0.00"));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.summary().netIncome()).isEqualByComparingTo("0");
        assertThat(response.funds().released().sessions()).isZero();
    }

    @Test
    void fundsSectionIsHiddenWhenTheTutorOnlyOffersFreeSubjects() {
        List<TutorSubject> onlyFree = List.of(
                subject(freeSubjectId, "Física 1", CompensationType.FREE, TutorSubjectStatus.ACTIVE, true));

        TutorStatisticsResponse response = calculator.calculate(new TutorStatisticsInput(
                PERIOD, 0, List.of(booking(BookingStatus.COMPLETED, MONDAY)), List.of(), List.of(), onlyFree));

        assertThat(response.funds()).isNull();
    }

    @Test
    void ratingDistributionHasFiveBucketsAndAveragesVisibleRatings() {
        List<TutorRatingRow> ratings = List.of(
                new TutorRatingRow(5, paidSubjectId), new TutorRatingRow(5, paidSubjectId),
                new TutorRatingRow(4, paidSubjectId), new TutorRatingRow(2, freeSubjectId));

        TutorStatisticsResponse response = calculate(List.of(), ratings, List.of());

        assertThat(response.ratings().total()).isEqualTo(4);
        assertThat(response.ratings().average()).isEqualTo(4.0);
        assertThat(response.ratings().buckets()).extracting("stars", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(5, 2),
                        org.assertj.core.groups.Tuple.tuple(4, 1),
                        org.assertj.core.groups.Tuple.tuple(3, 0),
                        org.assertj.core.groups.Tuple.tuple(2, 1),
                        org.assertj.core.groups.Tuple.tuple(1, 0));
        assertThat(response.summary().reviewsCount()).isEqualTo(4);
    }

    @Test
    void studentsWithTwoOrMoreCompletedSessionsAreRecurrent() {
        UUID recurrent = UUID.randomUUID();
        UUID occasional = UUID.randomUUID();
        List<TutorBookingRow> bookings = List.of(
                completedFor(recurrent, paidSubjectId), completedFor(recurrent, freeSubjectId),
                completedFor(recurrent, paidSubjectId), completedFor(occasional, paidSubjectId));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.students().distinct()).isEqualTo(2);
        assertThat(response.students().recurrent()).isEqualTo(1);
        assertThat(response.students().recurrentPercentage()).isEqualTo(50);
        assertThat(response.students().sessionsWithRecurrentStudents()).isEqualTo(3);
        assertThat(response.students().sessionsWithNewStudents()).isEqualTo(1);
    }

    @Test
    void occupancyDistinguishesUnofferedSlotsFromZeroPercentSlots() {
        // Lunes 09:00-11:00 ofrecido (mañana), 1 h reservada; lunes 14:00-16:00 ofrecido sin reservas.
        List<TutorSlotRow> slots = List.of(
                new TutorSlotRow(MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)),
                new TutorSlotRow(MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0)));
        List<TutorBookingRow> bookings = List.of(
                booking(BookingStatus.COMPLETED, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                booking(BookingStatus.CANCELLED, MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 0)));

        TutorStatisticsResponse response = calculate(bookings, List.of(), slots);

        OccupancyCellResponse morning = cell(response, DayOfWeek.MONDAY, AvailabilityBand.MORNING);
        OccupancyCellResponse afternoon = cell(response, DayOfWeek.MONDAY, AvailabilityBand.AFTERNOON);
        OccupancyCellResponse night = cell(response, DayOfWeek.MONDAY, AvailabilityBand.NIGHT);
        OccupancyCellResponse tuesday = cell(response, DayOfWeek.TUESDAY, AvailabilityBand.MORNING);

        assertThat(morning.percentage()).isEqualTo(50);
        assertThat(afternoon.percentage()).isZero();
        assertThat(night.percentage()).isNull();
        assertThat(tuesday.percentage()).isNull();
        assertThat(response.occupancy().offeredHours()).isEqualTo(4.0);
        assertThat(response.occupancy().bookedHours()).isEqualTo(1.0);
        assertThat(response.occupancy().percentage()).isEqualTo(25);
        assertThat(response.occupancy().cells()).hasSize(21);
    }

    @Test
    void occupancySplitsASlotThatCrossesBands() {
        List<TutorSlotRow> slots = List.of(new TutorSlotRow(MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0)));

        TutorStatisticsResponse response = calculate(List.of(), List.of(), slots);

        assertThat(cell(response, DayOfWeek.MONDAY, AvailabilityBand.MORNING).offeredHours()).isEqualTo(1.0);
        assertThat(cell(response, DayOfWeek.MONDAY, AvailabilityBand.AFTERNOON).offeredHours()).isEqualTo(1.0);
    }

    @Test
    void occupancyCountsASlotEndingAtMidnightUpToTheLastMinuteOfTheDay() {
        List<TutorSlotRow> slots = List.of(
                new TutorSlotRow(MONDAY, LocalTime.of(20, 0), com.knowlink.api.shared.utils.EndOfDay.TIME));
        List<TutorBookingRow> bookings = List.of(booking(
                BookingStatus.COMPLETED, MONDAY, LocalTime.of(23, 0), com.knowlink.api.shared.utils.EndOfDay.TIME));

        TutorStatisticsResponse response = calculate(bookings, List.of(), slots);

        OccupancyCellResponse night = cell(response, DayOfWeek.MONDAY, AvailabilityBand.NIGHT);
        assertThat(night.offeredHours()).isEqualTo(4.0);
        assertThat(night.bookedHours()).isEqualTo(1.0);
        assertThat(night.percentage()).isEqualTo(25);
    }

    @Test
    void occupancyPercentageNeverExceedsOneHundred() {
        List<TutorSlotRow> slots = List.of(new TutorSlotRow(MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        List<TutorBookingRow> bookings = List.of(
                booking(BookingStatus.COMPLETED, MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        TutorStatisticsResponse response = calculate(bookings, List.of(), slots);

        assertThat(cell(response, DayOfWeek.MONDAY, AvailabilityBand.MORNING).percentage()).isEqualTo(100);
    }

    @Test
    void subjectTableKeepsDeactivatedSubjectsOnlyWithActivityAndSortsByCompletedSessions() {
        List<TutorBookingRow> bookings = List.of(
                completedFor(UUID.randomUUID(), freeSubjectId),
                completedFor(UUID.randomUUID(), freeSubjectId),
                completedFor(UUID.randomUUID(), deactivatedSubjectId),
                completedFor(UUID.randomUUID(), paidSubjectId));

        TutorStatisticsResponse response = calculate(bookings, List.of(), List.of());

        assertThat(response.subjects()).extracting(SubjectPerformanceResponse::subjectName)
                .containsExactly("Física 1", "Análisis de Sistemas", "Lean Ágil");
        SubjectPerformanceResponse deactivated = response.subjects().get(2);
        assertThat(deactivated.deactivated()).isTrue();
        assertThat(deactivated.netIncome()).isNull();
    }

    @Test
    void deactivatedSubjectWithoutActivityIsOmitted() {
        TutorStatisticsResponse response = calculate(
                List.of(completedFor(UUID.randomUUID(), paidSubjectId)), List.of(), List.of());

        assertThat(response.subjects()).extracting(SubjectPerformanceResponse::subjectName)
                .doesNotContain("Lean Ágil");
    }

    // ── Reclamo detrás de los fondos retenidos ───────────────────────────────

    @Test
    void withheldClaimIdsFollowTheWithheldSessionsMostRecentFirst() {
        TutorBookingRow oldest = withheld(BookingStatus.NOT_CONFIRMED, MONDAY, LocalTime.of(9, 0));
        TutorBookingRow newestMorning = withheld(BookingStatus.NOT_CONFIRMED, MONDAY.plusDays(3), LocalTime.of(9, 0));
        TutorBookingRow newestAfternoon = withheld(BookingStatus.NOT_CONFIRMED, MONDAY.plusDays(3), LocalTime.of(15, 0));
        // Una reserva cancelada con fondos suspendidos y una liberada no cuentan como retenidas.
        TutorBookingRow cancelledWithClaim = withheld(BookingStatus.CANCELLED, MONDAY.plusDays(5), LocalTime.of(9, 0));
        TutorBookingRow released = funded(BookingStatus.COMPLETED, FundsStatus.RELEASED_TO_TUTOR, "1000.00", "970.00");

        UUID oldestClaim = UUID.randomUUID();
        UUID morningClaim = UUID.randomUUID();
        UUID afternoonClaim = UUID.randomUUID();
        TutorStatisticsResponse response = calculator.calculate(new TutorStatisticsInput(
                PERIOD, 0, List.of(oldest, newestMorning, newestAfternoon, cancelledWithClaim, released),
                List.of(), List.of(), subjects,
                Map.of(oldest.bookingId(), oldestClaim,
                        newestMorning.bookingId(), morningClaim,
                        newestAfternoon.bookingId(), afternoonClaim,
                        cancelledWithClaim.bookingId(), UUID.randomUUID(),
                        released.bookingId(), UUID.randomUUID())));

        assertThat(response.funds().withheldByClaim().sessions()).isEqualTo(3);
        assertThat(response.funds().withheldClaimIds())
                .containsExactly(afternoonClaim, morningClaim, oldestClaim);
    }

    @Test
    void withheldSessionsWithoutAnActiveClaimAreSkippedInTheIds() {
        TutorBookingRow withClaim = withheld(BookingStatus.NOT_CONFIRMED, MONDAY, LocalTime.of(9, 0));
        TutorBookingRow withoutClaim = withheld(BookingStatus.NOT_CONFIRMED, MONDAY.plusDays(1), LocalTime.of(9, 0));
        UUID claim = UUID.randomUUID();

        TutorStatisticsResponse response = calculator.calculate(new TutorStatisticsInput(
                PERIOD, 0, List.of(withClaim, withoutClaim), List.of(), List.of(), subjects,
                Map.of(withClaim.bookingId(), claim)));

        // Los fondos siguen contando las dos sesiones; solo falta el reclamo de una.
        assertThat(response.funds().withheldByClaim().sessions()).isEqualTo(2);
        assertThat(response.funds().withheldClaimIds()).containsExactly(claim);
    }

    @Test
    void withheldClaimIdsAreEmptyWhenThereIsNoClaimData() {
        TutorStatisticsResponse response = calculate(
                List.of(withheld(BookingStatus.NOT_CONFIRMED, MONDAY, LocalTime.of(9, 0))), List.of(), List.of());

        assertThat(response.funds().withheldByClaim().sessions()).isEqualTo(1);
        assertThat(response.funds().withheldClaimIds()).isEmpty();
    }

    @Test
    void cancelledBookingsAreCountedWhateverTheirSessionDate() {
        // El repositorio devuelve también las clases posteriores al fin del período que se cancelaron
        // dentro de él: el calculador las cuenta sin volver a filtrar por la fecha de la clase.
        LocalDate afterThePeriod = PERIOD.to().plusDays(1);
        TutorBookingRow cancelledForTomorrow = new TutorBookingRow(UUID.randomUUID(), afterThePeriod,
                LocalTime.of(9, 0), LocalTime.of(10, 0), BookingStatus.CANCELLED, UUID.randomUUID(), paidSubjectId,
                CancellationRole.STUDENT, RefundPolicy.REFUND_TOTAL_STUDENT, null, null, null, null);

        TutorStatisticsResponse response = calculate(List.of(cancelledForTomorrow), List.of(), List.of());

        assertThat(response.unheldSessions().cancelledByStudent()).isEqualTo(1);
        assertThat(response.hasActivity()).isTrue();
    }

    @Test
    void previousPeriodCompletedCountIsPassedThrough() {
        TutorStatisticsResponse response = calculator.calculate(new TutorStatisticsInput(
                PERIOD, 7, List.of(), List.of(), List.of(), subjects));

        assertThat(response.summary().completedSessionsPreviousPeriod()).isEqualTo(7);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TutorStatisticsResponse calculate(
            List<TutorBookingRow> bookings, List<TutorRatingRow> ratings, List<TutorSlotRow> slots) {
        return calculator.calculate(new TutorStatisticsInput(PERIOD, 0, bookings, ratings, slots, subjects));
    }

    private OccupancyCellResponse cell(TutorStatisticsResponse response, DayOfWeek day, AvailabilityBand band) {
        return response.occupancy().cells().stream()
                .filter(cell -> cell.dayOfWeek() == day && cell.band() == band)
                .findFirst()
                .orElseThrow();
    }

    private TutorSubject subject(UUID id, String name, CompensationType type, TutorSubjectStatus status,
            boolean active) {
        return TutorSubject.builder()
                .tutorSubjectId(id)
                .subject(Subject.builder().name(name).build())
                .compensationType(type)
                .tutorSubjectStatus(status)
                .active(active)
                .pricePerHour(type == CompensationType.PAID ? new BigDecimal("15000") : null)
                .build();
    }

    private TutorBookingRow booking(BookingStatus status, LocalDate date) {
        return booking(status, date, LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    private TutorBookingRow booking(BookingStatus status, LocalDate date, LocalTime start, LocalTime end) {
        return new TutorBookingRow(UUID.randomUUID(), date, start, end, status, UUID.randomUUID(),
                paidSubjectId, null, null, null, null, null, null);
    }

    private TutorBookingRow completedFor(UUID studentId, UUID subjectId) {
        return new TutorBookingRow(UUID.randomUUID(), MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                BookingStatus.COMPLETED, studentId, subjectId, null, null, null, null, null, null);
    }

    private TutorBookingRow cancelled(CancellationRole by, RefundPolicy policy) {
        return new TutorBookingRow(UUID.randomUUID(), MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                BookingStatus.CANCELLED, UUID.randomUUID(), paidSubjectId, by, policy, null, null, null, null);
    }

    /** Sesión con los fondos suspendidos por un reclamo, en la fecha y hora indicadas. */
    private TutorBookingRow withheld(BookingStatus status, LocalDate date, LocalTime start) {
        return new TutorBookingRow(UUID.randomUUID(), date, start, start.plusHours(1), status,
                UUID.randomUUID(), paidSubjectId, null, null, FundsStatus.SUSPENDED_BY_CLAIM, FundsRecipient.TUTOR,
                new BigDecimal("1000.00"), new BigDecimal("0.00"));
    }

    private TutorBookingRow funded(BookingStatus status, FundsStatus fundsStatus, String original, String transferred) {
        return new TutorBookingRow(UUID.randomUUID(), MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), status,
                UUID.randomUUID(), paidSubjectId, null, null, fundsStatus,
                fundsStatus == FundsStatus.REFUNDED_TO_STUDENT ? FundsRecipient.STUDENT : FundsRecipient.TUTOR,
                new BigDecimal(original), new BigDecimal(transferred));
    }
}
