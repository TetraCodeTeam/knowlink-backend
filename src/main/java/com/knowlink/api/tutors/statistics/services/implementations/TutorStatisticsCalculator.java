package com.knowlink.api.tutors.statistics.services.implementations;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.enums.CancellationRole;
import com.knowlink.api.bookings.data.enums.RefundPolicy;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.domain.FundsStatus;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.statistics.controllers.responses.FundsAmountResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.FundsStatusResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.MonthlySessionsResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.OccupancyCellResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.OccupancyResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.RatingBucketResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.RatingDistributionResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.StatisticsPeriodResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.StatisticsSummaryResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.StudentsStatisticsResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.SubjectPerformanceResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.TutorStatisticsResponse;
import com.knowlink.api.tutors.statistics.controllers.responses.UnheldSessionsResponse;
import com.knowlink.api.tutors.statistics.data.enums.AvailabilityBand;
import com.knowlink.api.tutors.statistics.data.projections.TutorBookingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorRatingRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorSlotRow;
import com.knowlink.api.tutors.statistics.data.projections.TutorStatisticsInput;
import com.knowlink.api.tutors.statistics.utils.StatisticsPeriod;
import com.knowlink.api.tutors.statistics.utils.TutorStatisticsConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Convierte las filas crudas del período en las métricas del panel. No accede a la base de datos,
 * así que cada regla de negocio de la US-19 se puede probar de forma aislada.
 */
@Component
public class TutorStatisticsCalculator {

    private static final int RECURRENT_MIN_SESSIONS = 2;
    private static final int MAX_STARS = 5;
    private static final int DAYS = DayOfWeek.values().length;
    private static final int BANDS = AvailabilityBand.values().length;

    private final BigDecimal commissionPercentage;

    public TutorStatisticsCalculator(
            @Value("${knowlink.funds.retention-percentage:3.00}") BigDecimal commissionPercentage) {
        this.commissionPercentage = commissionPercentage;
    }

    public TutorStatisticsResponse calculate(TutorStatisticsInput input) {
        StatisticsPeriod period = input.period();
        List<TutorBookingRow> bookings = input.bookings();
        List<TutorBookingRow> completed = filter(bookings, row -> row.status() == BookingStatus.COMPLETED);

        boolean hasActivity = bookings.stream()
                .anyMatch(row -> TutorStatisticsConstants.ACTIVITY_STATUSES.contains(row.status()));

        return new TutorStatisticsResponse(
                new StatisticsPeriodResponse(period.type(), period.from(), period.to()),
                hasActivity,
                summary(input, completed),
                monthlySessions(period, completed),
                unheldSessions(bookings),
                funds(input),
                ratingDistribution(input.ratings()),
                students(completed),
                occupancy(input),
                subjects(input, completed));
    }

    // ── Resumen ──────────────────────────────────────────────────────────────

    private StatisticsSummaryResponse summary(TutorStatisticsInput input, List<TutorBookingRow> completed) {
        long notFulfilled = count(input.bookings(), BookingStatus.NOT_FULFILLED_BY_TUTOR);
        long notHeld = count(input.bookings(), BookingStatus.SESSION_NOT_HELD);
        // Las cancelaciones quedan fuera de la tasa: ni suman ni restan al cumplimiento.
        int eligible = (int) (completed.size() + notFulfilled + notHeld);
        Double fulfillmentRate = eligible == 0 ? null : round(completed.size() * 100.0 / eligible, 1);

        List<TutorRatingRow> ratings = input.ratings();
        BigDecimal netIncome = input.bookings().stream()
                .map(this::netIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StatisticsSummaryResponse(
                completed.size(),
                (int) input.completedSessionsPreviousPeriod(),
                fulfillmentRate,
                eligible,
                average(ratings),
                ratings.size(),
                money(netIncome),
                commissionPercentage);
    }

    private List<MonthlySessionsResponse> monthlySessions(StatisticsPeriod period, List<TutorBookingRow> completed) {
        Map<YearMonth, Long> perMonth = completed.stream()
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.sessionDate()), Collectors.counting()));

        List<MonthlySessionsResponse> months = new ArrayList<>();
        YearMonth last = YearMonth.from(period.to());
        for (YearMonth month = YearMonth.from(period.from()); !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(new MonthlySessionsResponse(month.toString(), perMonth.getOrDefault(month, 0L).intValue()));
        }
        return months;
    }

    // ── Clases que no se dieron ──────────────────────────────────────────────

    private UnheldSessionsResponse unheldSessions(List<TutorBookingRow> bookings) {
        List<TutorBookingRow> cancelled = filter(bookings, row -> row.status() == BookingStatus.CANCELLED);
        int byTutor = (int) cancelled.stream().filter(row -> row.cancelledBy() == CancellationRole.TUTOR).count();
        List<TutorBookingRow> byStudent = filter(cancelled, row -> row.cancelledBy() == CancellationRole.STUDENT);
        int credited = (int) byStudent.stream()
                .filter(row -> row.refundPolicy() == RefundPolicy.TRANSFER_TOTAL_TUTOR)
                .count();
        int notHeld = (int) (count(bookings, BookingStatus.NOT_FULFILLED_BY_TUTOR)
                + count(bookings, BookingStatus.SESSION_NOT_HELD));
        return new UnheldSessionsResponse(byTutor, byStudent.size(), credited, notHeld);
    }

    // ── Fondos ───────────────────────────────────────────────────────────────

    /** Null si el tutor solo ofrece materias gratuitas: la sección no se muestra (criterio 6). */
    private FundsStatusResponse funds(TutorStatisticsInput input) {
        boolean offersPaidSubject = input.subjects().stream()
                .anyMatch(subject -> subject.getCompensationType() == CompensationType.PAID
                        && subject.getTutorSubjectStatus() == TutorSubjectStatus.ACTIVE);
        if (!offersPaidSubject) {
            return null;
        }
        List<TutorBookingRow> bookings = input.bookings();
        return new FundsStatusResponse(
                fundsAmount(filter(bookings, this::isReleased), row -> row.transferredAmount()),
                fundsAmount(filter(bookings, this::isPendingRelease), this::netOfCommission),
                fundsAmount(filter(bookings, this::isWithheldByClaim), this::netOfCommission),
                commissionPercentage);
    }

    private FundsAmountResponse fundsAmount(
            List<TutorBookingRow> rows, java.util.function.Function<TutorBookingRow, BigDecimal> amount) {
        BigDecimal total = rows.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FundsAmountResponse(money(total), rows.size());
    }

    private boolean hasAmount(TutorBookingRow row) {
        return row.originalAmount() != null && row.originalAmount().signum() > 0;
    }

    private boolean isReleased(TutorBookingRow row) {
        return hasAmount(row)
                && row.fundsStatus() == FundsStatus.RELEASED_TO_TUTOR
                && row.fundsRecipient() == FundsRecipient.TUTOR;
    }

    /** Las reservas canceladas o vencidas conservan la fila de fondos en HELD: no son pendientes. */
    private boolean isPendingRelease(TutorBookingRow row) {
        return hasAmount(row)
                && row.fundsStatus() == FundsStatus.HELD
                && TutorStatisticsConstants.PENDING_RELEASE_STATUSES.contains(row.status());
    }

    private boolean isWithheldByClaim(TutorBookingRow row) {
        return hasAmount(row)
                && row.fundsStatus() == FundsStatus.SUSPENDED_BY_CLAIM
                && row.status() != BookingStatus.CANCELLED;
    }

    /** Mismo redondeo que FundsLedgerService al liberar: se redondea la comisión y se resta. */
    private BigDecimal netOfCommission(TutorBookingRow row) {
        BigDecimal percentage = row.retentionPercentage() != null ? row.retentionPercentage() : commissionPercentage;
        BigDecimal commission = row.originalAmount().multiply(percentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return row.originalAmount().subtract(commission);
    }

    /** Ingreso neto de una reserva: lo ya liberado más lo pendiente de liberación. */
    private BigDecimal netIncome(TutorBookingRow row) {
        if (isReleased(row)) {
            return row.transferredAmount();
        }
        if (isPendingRelease(row)) {
            return netOfCommission(row);
        }
        return BigDecimal.ZERO;
    }

    // ── Calificaciones ───────────────────────────────────────────────────────

    private RatingDistributionResponse ratingDistribution(List<TutorRatingRow> ratings) {
        Map<Integer, Long> perScore = ratings.stream()
                .collect(Collectors.groupingBy(TutorRatingRow::score, Collectors.counting()));
        List<RatingBucketResponse> buckets = new ArrayList<>();
        for (int stars = MAX_STARS; stars >= 1; stars--) {
            buckets.add(new RatingBucketResponse(stars, perScore.getOrDefault(stars, 0L).intValue()));
        }
        return new RatingDistributionResponse(average(ratings), ratings.size(), buckets);
    }

    private Double average(List<TutorRatingRow> ratings) {
        if (ratings.isEmpty()) {
            return null;
        }
        return round(ratings.stream().mapToInt(TutorRatingRow::score).average().orElse(0), 1);
    }

    // ── Alumnos ──────────────────────────────────────────────────────────────

    private StudentsStatisticsResponse students(List<TutorBookingRow> completed) {
        Map<UUID, Long> sessionsPerStudent = sessionsPerStudent(completed);
        int distinct = sessionsPerStudent.size();
        int recurrent = (int) sessionsPerStudent.values().stream()
                .filter(sessions -> sessions >= RECURRENT_MIN_SESSIONS)
                .count();
        int sessionsWithRecurrent = (int) sessionsPerStudent.values().stream()
                .filter(sessions -> sessions >= RECURRENT_MIN_SESSIONS)
                .mapToLong(Long::longValue)
                .sum();
        int percentage = distinct == 0 ? 0 : (int) Math.round(recurrent * 100.0 / distinct);
        return new StudentsStatisticsResponse(
                distinct, recurrent, percentage, sessionsWithRecurrent, completed.size() - sessionsWithRecurrent);
    }

    private Map<UUID, Long> sessionsPerStudent(List<TutorBookingRow> completed) {
        return completed.stream()
                .collect(Collectors.groupingBy(TutorBookingRow::studentId, Collectors.counting()));
    }

    // ── Ocupación ────────────────────────────────────────────────────────────

    private OccupancyResponse occupancy(TutorStatisticsInput input) {
        int[][] offered = new int[DAYS][BANDS];
        int[][] booked = new int[DAYS][BANDS];

        for (TutorSlotRow slot : input.offeredSlots()) {
            accumulate(offered, slot.date().getDayOfWeek(), slot.startTime(), slot.endTime());
        }
        for (TutorBookingRow row : input.bookings()) {
            if (TutorStatisticsConstants.RESERVED_STATUSES.contains(row.status())) {
                accumulate(booked, row.sessionDate().getDayOfWeek(), row.startTime(), row.endTime());
            }
        }

        List<OccupancyCellResponse> cells = new ArrayList<>();
        int totalOffered = 0;
        int totalBooked = 0;
        for (DayOfWeek day : DayOfWeek.values()) {
            for (AvailabilityBand band : AvailabilityBand.values()) {
                int offeredMinutes = offered[day.ordinal()][band.ordinal()];
                int bookedMinutes = booked[day.ordinal()][band.ordinal()];
                totalOffered += offeredMinutes;
                totalBooked += bookedMinutes;
                cells.add(new OccupancyCellResponse(
                        day, band, hours(bookedMinutes), hours(offeredMinutes),
                        percentage(bookedMinutes, offeredMinutes)));
            }
        }
        return new OccupancyResponse(
                hours(totalBooked), hours(totalOffered), percentage(totalBooked, totalOffered), cells);
    }

    private void accumulate(
            int[][] target, DayOfWeek day, java.time.LocalTime start, java.time.LocalTime end) {
        int startMinute = AvailabilityBand.minuteOfDay(start);
        int endMinute = AvailabilityBand.endMinuteOfDay(end);
        for (AvailabilityBand band : AvailabilityBand.values()) {
            target[day.ordinal()][band.ordinal()] += band.overlapMinutes(startMinute, endMinute);
        }
    }

    /** Null cuando no hubo horas ofrecidas; se acota a 100 porque el histórico previo al archivado puede estar incompleto. */
    private Integer percentage(int bookedMinutes, int offeredMinutes) {
        if (offeredMinutes == 0) {
            return null;
        }
        return Math.min(100, (int) Math.round(bookedMinutes * 100.0 / offeredMinutes));
    }

    private double hours(int minutes) {
        return round(minutes / 60.0, 2);
    }

    // ── Desempeño por materia ────────────────────────────────────────────────

    private List<SubjectPerformanceResponse> subjects(TutorStatisticsInput input, List<TutorBookingRow> completed) {
        Map<UUID, List<TutorBookingRow>> completedBySubject = completed.stream()
                .collect(Collectors.groupingBy(TutorBookingRow::tutorSubjectId));
        Map<UUID, List<TutorRatingRow>> ratingsBySubject = input.ratings().stream()
                .collect(Collectors.groupingBy(TutorRatingRow::tutorSubjectId));
        Map<UUID, BigDecimal> incomeBySubject = new HashMap<>();
        Set<UUID> subjectsWithActivity = new HashSet<>();
        for (TutorBookingRow row : input.bookings()) {
            subjectsWithActivity.add(row.tutorSubjectId());
            incomeBySubject.merge(row.tutorSubjectId(), netIncome(row), BigDecimal::add);
        }

        return input.subjects().stream()
                // Las materias desactivadas se incluyen solo si tuvieron actividad en el período.
                .filter(subject -> subject.getTutorSubjectStatus() == TutorSubjectStatus.ACTIVE
                        || subjectsWithActivity.contains(subject.getTutorSubjectId()))
                .map(subject -> subjectPerformance(
                        subject,
                        completedBySubject.getOrDefault(subject.getTutorSubjectId(), List.of()),
                        ratingsBySubject.getOrDefault(subject.getTutorSubjectId(), List.of()),
                        incomeBySubject.getOrDefault(subject.getTutorSubjectId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparingInt(SubjectPerformanceResponse::completedSessions).reversed()
                        .thenComparing(SubjectPerformanceResponse::subjectName))
                .toList();
    }

    private SubjectPerformanceResponse subjectPerformance(
            TutorSubject subject,
            List<TutorBookingRow> completed,
            List<TutorRatingRow> ratings,
            BigDecimal income) {
        StudentsStatisticsResponse students = students(completed);
        boolean free = subject.getCompensationType() == CompensationType.FREE;
        return new SubjectPerformanceResponse(
                subject.getTutorSubjectId(),
                subject.getSubject().getName(),
                subject.getCompensationType(),
                subject.getPricePerHour(),
                subject.getTutorSubjectStatus() != TutorSubjectStatus.ACTIVE,
                completed.size(),
                average(ratings),
                ratings.size(),
                students.distinct(),
                students.recurrent(),
                free ? null : money(income));
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private List<TutorBookingRow> filter(List<TutorBookingRow> rows, Predicate<TutorBookingRow> predicate) {
        return rows.stream().filter(predicate).toList();
    }

    private long count(List<TutorBookingRow> rows, BookingStatus status) {
        return rows.stream().filter(row -> row.status() == status).count();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
